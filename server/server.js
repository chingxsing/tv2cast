require('dotenv').config();
const express = require('express');
const cors = require('cors');
const path = require('path');
const fs = require('fs');
const mime = require('mime-types');
const os = require('os');
const { scanAllDirectories, findVideoById } = require('./scanner');
const { probeFile, findExternalSubtitles, srtToVtt, assToVtt } = require('./tracks');
const dgram = require('dgram');

const app = express();
const PORT = process.env.PORT || 3456;
const VIDEO_DIRS = (process.env.VIDEO_DIRS || '').split(',').filter(Boolean);

// Middleware
app.use(cors());
app.use(express.json());

// Cache scanned videos (re-scan on request or every 5 minutes)
let videoCache = [];
let lastScanTime = 0;
const CACHE_TTL = 5 * 60 * 1000; // 5 minutes

function getVideos(forceRescan = false) {
    const now = Date.now();
    if (forceRescan || videoCache.length === 0 || (now - lastScanTime) > CACHE_TTL) {
        console.log('📂 Scanning video directories...');
        videoCache = scanAllDirectories(VIDEO_DIRS);
        lastScanTime = now;
        console.log(`✅ Found ${videoCache.length} video files`);
    }
    return videoCache;
}

// ──────────────────────────────────────────
// API Routes
// ──────────────────────────────────────────

/**
 * GET /api/videos — List all video files
 */
app.get('/api/videos', (req, res) => {
    const forceRescan = req.query.rescan === 'true';
    const videos = getVideos(forceRescan);

    // Search filter
    const search = (req.query.search || '').toLowerCase();
    let filtered = videos;

    if (search) {
        filtered = videos.filter(v =>
            v.name.toLowerCase().includes(search) ||
            v.filename.toLowerCase().includes(search) ||
            v.directory.toLowerCase().includes(search)
        );
    }

    // Return without file paths for security (only id, name, metadata)
    const result = filtered.map(v => ({
        id: v.id,
        name: v.name,
        filename: v.filename,
        extension: v.extension,
        size: v.size,
        sizeFormatted: v.sizeFormatted,
        modified: v.modified,
        directory: v.directory
    }));

    res.json({
        total: result.length,
        videos: result
    });
});

/**
 * GET /api/videos/:id — Get single video info
 */
app.get('/api/videos/:id', (req, res) => {
    const videos = getVideos();
    const video = findVideoById(videos, req.params.id);

    if (!video) {
        return res.status(404).json({ error: 'Video not found' });
    }

    res.json({
        id: video.id,
        name: video.name,
        filename: video.filename,
        extension: video.extension,
        size: video.size,
        sizeFormatted: video.sizeFormatted,
        modified: video.modified,
        directory: video.directory
    });
});

/**
 * GET /api/videos/:id/compat-stream — Stream video with Real-time Transcoding (720p H.264)
 * Solves stuttering on low-powered devices like TV browsers.
 */
app.get('/api/videos/:id/compat-stream', (req, res) => {
    const videos = getVideos();
    const video = findVideoById(videos, req.params.id);

    if (!video) {
        return res.status(404).json({ error: 'Video not found' });
    }

    const filePath = video.path;
    const { spawn } = require('child_process');

    console.log(`🚀 Transcoding: ${video.name} (Compatibility Mode)`);

    // FFmpeg command for real-time 720p H.264 transcoding
    // Includes HDR-to-SDR tone mapping for better compatibility with non-HDR displays
    // Using a filter chain that works with standard FFmpeg (no zscale required)
    const ffmpeg = spawn('ffmpeg', [
        '-i', filePath,                             // Input file
        '-vf', 'format=p010,tonemap=hable,format=yuv420p,scale=1280:-2', // HDR to SDR + Scale
        '-c:v', 'libx264',                          // Video codec: H.264
        '-preset', 'ultrafast',                     // Minimum CPU usage, fastest speed
        '-crf', '23',                               // Constant Rate Factor (good balance)
        '-maxrate', '4000k',                        // Max bitrate
        '-bufsize', '8000k',                        // Buffer size
        '-c:a', 'aac',                              // Audio codec: AAC
        '-ac', '2',                                 // Channels: Stereo
        '-b:a', '192k',                             // Audio bitrate
        '-f', 'matroska',                           // Container: MKV (robust for streaming)
        'pipe:1'                                    // Output to stdout
    ]);

    res.writeHead(200, {
        'Content-Type': 'video/x-matroska',
        'Transfer-Encoding': 'chunked'
    });

    ffmpeg.stdout.pipe(res);

    // Error handling
    ffmpeg.stderr.on('data', (data) => {
        // Log errors only if needed, FFmpeg output can be verbose
        // console.log(`FFmpeg: ${data}`);
    });

    ffmpeg.on('error', (err) => {
        console.error('FFmpeg spawn error:', err);
    });

    // Cleanup process when client disconnects
    req.on('close', () => {
        console.log(`🛑 Stopping transcoding: ${video.name}`);
        ffmpeg.kill('SIGKILL');
    });
});

/**
 * GET /api/videos/:id/stream — Stream video with Range support
 */
app.get('/api/videos/:id/stream', (req, res) => {
    const videos = getVideos();
    const video = findVideoById(videos, req.params.id);

    if (!video) {
        return res.status(404).json({ error: 'Video not found' });
    }

    const filePath = video.path;
    const stat = fs.statSync(filePath);
    const fileSize = stat.size;
    const mimeType = mime.lookup(filePath) || 'video/mp4';

    const range = req.headers.range;

    if (range) {
        // Parse Range header
        const parts = range.replace(/bytes=/, '').split('-');
        const start = parseInt(parts[0], 10);
        const end = parts[1] ? parseInt(parts[1], 10) : fileSize - 1;

        if (start >= fileSize) {
            res.status(416).set('Content-Range', `bytes */${fileSize}`);
            return res.end();
        }

        const chunksize = (end - start) + 1;
        const stream = fs.createReadStream(filePath, { start, end });

        res.writeHead(206, {
            'Content-Range': `bytes ${start}-${end}/${fileSize}`,
            'Accept-Ranges': 'bytes',
            'Content-Length': chunksize,
            'Content-Type': mimeType,
        });

        stream.pipe(res);
    } else {
        // No range request — send entire file
        res.writeHead(200, {
            'Content-Length': fileSize,
            'Content-Type': mimeType,
            'Accept-Ranges': 'bytes',
        });

        fs.createReadStream(filePath).pipe(res);
    }
});

/**
 * GET /api/videos/:id/tracks — Get audio & subtitle tracks info
 */
app.get('/api/videos/:id/tracks', async (req, res) => {
    const videos = getVideos();
    const video = findVideoById(videos, req.params.id);

    if (!video) {
        return res.status(404).json({ error: 'Video not found' });
    }

    try {
        // Probe embedded tracks
        const probeData = await probeFile(video.path);

        // Find external subtitle files
        const externalSubs = findExternalSubtitles(video.path);

        // Merge external subs into subtitle tracks
        const allSubtitles = [
            ...probeData.subtitleTracks,
            ...externalSubs.map((s, i) => ({
                index: 1000 + i,
                label: s.label,
                language: s.language,
                codec: s.format,
                default: false,
                forced: false,
                embedded: false,
                filename: s.filename
            }))
        ];

        res.json({
            audioTracks: probeData.audioTracks,
            subtitleTracks: allSubtitles,
            duration: probeData.duration
        });
    } catch (err) {
        console.error('Track probe error:', err);
        res.json({ audioTracks: [], subtitleTracks: [], duration: null });
    }
});

/**
 * GET /api/videos/:id/subtitle/:trackIndex — Extract embedded subtitle as WebVTT
 */
app.get('/api/videos/:id/subtitle/:trackIndex', async (req, res) => {
    const videos = getVideos();
    const video = findVideoById(videos, req.params.id);
    const trackIndex = parseInt(req.params.trackIndex, 10);

    if (!video) {
        return res.status(404).json({ error: 'Video not found' });
    }

    // External subtitle (index >= 1000)
    if (trackIndex >= 1000) {
        const externalSubs = findExternalSubtitles(video.path);
        const sub = externalSubs[trackIndex - 1000];
        if (!sub) return res.status(404).json({ error: 'Subtitle not found' });

        const content = fs.readFileSync(sub.path, 'utf-8');
        let vtt;

        if (sub.format === 'vtt') {
            vtt = content;
        } else if (sub.format === 'srt') {
            vtt = srtToVtt(content);
        } else if (sub.format === 'ass' || sub.format === 'ssa') {
            vtt = assToVtt(content);
        } else {
            return res.status(400).json({ error: 'Unsupported subtitle format' });
        }

        res.set('Content-Type', 'text/vtt; charset=utf-8');
        return res.send(vtt);
    }

    // Embedded subtitle — extract with ffmpeg
    const { execFile } = require('child_process');
    execFile('ffmpeg', [
        '-i', video.path,
        '-map', `0:${trackIndex}`,
        '-f', 'webvtt',
        '-'
    ], { timeout: 30000, maxBuffer: 10 * 1024 * 1024 }, (err, stdout) => {
        if (err) {
            console.error('Subtitle extract error:', err.message);
            return res.status(500).json({ error: 'Failed to extract subtitle' });
        }
        res.set('Content-Type', 'text/vtt; charset=utf-8');
        res.send(stdout);
    });
});

/**
 * POST /api/rescan — Force rescan directories
 */
app.post('/api/rescan', (req, res) => {
    const videos = getVideos(true);
    res.json({ message: 'Rescan complete', total: videos.length });
});

/**
 * GET /api/server-info — Server info for Android TV discovery
 */
app.get('/api/server-info', (req, res) => {
    const interfaces = os.networkInterfaces();
    const addresses = [];

    for (const name of Object.keys(interfaces)) {
        for (const iface of interfaces[name]) {
            if (iface.family === 'IPv4' && !iface.internal) {
                addresses.push({ name, address: iface.address });
            }
        }
    }

    res.json({
        name: 'Tv2Cast Server',
        version: '1.0.0',
        port: PORT,
        addresses,
        videoCount: getVideos().length
    });
});

// ──────────────────────────────────────────
// Serve Static Files & SPA Fallback
// ──────────────────────────────────────────

// Serve static files (Web UI)
app.use(express.static(path.join(__dirname, 'public')));

app.get('*', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// ──────────────────────────────────────────
// Start Server
// ──────────────────────────────────────────
app.listen(PORT, '0.0.0.0', () => {
    console.log('');
    console.log('╔═══════════════════════════════════════════╗');
    console.log('║         🎬  Tv2Cast Server  🎬            ║');
    console.log('╠═══════════════════════════════════════════╣');
    console.log(`║  Local:   http://localhost:${PORT}          ║`);

    // Show LAN IP for remote access
    const interfaces = os.networkInterfaces();
    for (const name of Object.keys(interfaces)) {
        for (const iface of interfaces[name]) {
            if (iface.family === 'IPv4' && !iface.internal) {
                const url = `http://${iface.address}:${PORT}`;
                console.log(`║  LAN:     ${url.padEnd(30)}║`);
            }
        }
    }

    console.log('╠═══════════════════════════════════════════╣');
    console.log(`║  Scanning: ${VIDEO_DIRS.length} director${VIDEO_DIRS.length === 1 ? 'y' : 'ies'}`);
    VIDEO_DIRS.forEach(d => console.log(`║    📁 ${d.trim()}`));
    console.log('╚═══════════════════════════════════════════╝');
    console.log('');

    // Initial scan
    getVideos();
});

// ──────────────────────────────────────────
// UDP Discovery Server (Port 3457)
// ──────────────────────────────────────────
const discoveryServer = dgram.createSocket('udp4');

discoveryServer.on('message', (msg, rinfo) => {
    if (msg.toString() === 'TV2CAST_DISCOVER') {
        const response = JSON.stringify({
            name: 'Tv2Cast Server',
            port: PORT,
            id: 'tv2cast-v1'
        });
        console.log(`📡 Discovery request from ${rinfo.address}:${rinfo.port}`);
        discoveryServer.send(response, rinfo.port, rinfo.address);
    }
});

discoveryServer.on('error', (err) => {
    console.error(`📡 Discovery error: ${err.stack}`);
    discoveryServer.close();
});

discoveryServer.bind(3457, () => {
    console.log('📡 Discovery server listening on UDP port 3457');
});
