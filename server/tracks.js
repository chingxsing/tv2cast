const { execFile } = require('child_process');
const path = require('path');
const fs = require('fs');

const SUBTITLE_EXTENSIONS = new Set(['.srt', '.vtt', '.ass', '.ssa', '.sub']);

/**
 * Use ffprobe to get audio and subtitle tracks embedded in a video file
 */
function probeFile(filePath) {
    return new Promise((resolve) => {
        execFile('ffprobe', [
            '-v', 'quiet',
            '-print_format', 'json',
            '-show_streams',
            '-show_format',
            filePath
        ], { timeout: 15000 }, (err, stdout) => {
            if (err) {
                resolve({ audioTracks: [], subtitleTracks: [], duration: null });
                return;
            }

            try {
                const data = JSON.parse(stdout);
                const streams = data.streams || [];

                const audioTracks = streams
                    .filter(s => s.codec_type === 'audio')
                    .map((s, i) => ({
                        index: s.index,
                        label: s.tags?.title || s.tags?.language || `Audio ${i + 1}`,
                        language: s.tags?.language || 'und',
                        codec: s.codec_name,
                        channels: s.channels,
                        default: (s.disposition?.default === 1)
                    }));

                const subtitleTracks = streams
                    .filter(s => s.codec_type === 'subtitle')
                    .map((s, i) => ({
                        index: s.index,
                        label: s.tags?.title || s.tags?.language || `Subtitle ${i + 1}`,
                        language: s.tags?.language || 'und',
                        codec: s.codec_name,
                        default: (s.disposition?.default === 1),
                        forced: (s.disposition?.forced === 1),
                        embedded: true
                    }));

                const duration = data.format?.duration
                    ? parseFloat(data.format.duration)
                    : null;

                resolve({ audioTracks, subtitleTracks, duration });
            } catch {
                resolve({ audioTracks: [], subtitleTracks: [], duration: null });
            }
        });
    });
}

/**
 * Scan for external subtitle files alongside a video
 * e.g. movie.srt, movie.en.srt, movie.th.vtt
 */
function findExternalSubtitles(videoPath) {
    const dir = path.dirname(videoPath);
    const baseName = path.basename(videoPath, path.extname(videoPath));
    const results = [];

    try {
        const files = fs.readdirSync(dir);
        for (const file of files) {
            const ext = path.extname(file).toLowerCase();
            if (!SUBTITLE_EXTENSIONS.has(ext)) continue;

            const subBase = path.basename(file, ext);
            // Match: movie.srt, movie.en.srt, movie.English.srt, etc.
            if (subBase === baseName || subBase.startsWith(baseName + '.')) {
                const langPart = subBase.slice(baseName.length + 1) || '';
                const language = langPart || guessLanguageFromExt(ext, file);

                results.push({
                    filename: file,
                    path: path.join(dir, file),
                    format: ext.slice(1), // srt, vtt, ass
                    language: language || 'und',
                    label: language || file,
                    embedded: false
                });
            }
        }
    } catch {
        // ignore
    }

    return results;
}

/**
 * Convert SRT content to WebVTT format (browsers only support VTT)
 */
function srtToVtt(srtContent) {
    let vtt = 'WEBVTT\n\n';
    // Replace , with . in timestamps and clean up
    vtt += srtContent
        .replace(/\r\n/g, '\n')
        .replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, '$1.$2')
        .replace(/^\d+\n/gm, '') // Remove sequence numbers at start of lines
        .trim();
    return vtt;
}

/**
 * Convert ASS/SSA content to WebVTT (basic conversion)
 */
function assToVtt(assContent) {
    let vtt = 'WEBVTT\n\n';
    const lines = assContent.split(/\r?\n/);
    let seqNum = 1;

    for (const line of lines) {
        if (!line.startsWith('Dialogue:')) continue;
        const parts = line.substring(9).split(',');
        if (parts.length < 10) continue;

        const start = convertAssTime(parts[1].trim());
        const end = convertAssTime(parts[2].trim());
        // Text is everything after the 9th comma
        let text = parts.slice(9).join(',')
            .replace(/\{[^}]*\}/g, '') // Remove ASS formatting tags
            .replace(/\\N/g, '\n')
            .replace(/\\n/g, '\n')
            .trim();

        if (text) {
            vtt += `${seqNum}\n${start} --> ${end}\n${text}\n\n`;
            seqNum++;
        }
    }

    return vtt;
}

function convertAssTime(assTime) {
    // ASS format: H:MM:SS.CC -> WebVTT: HH:MM:SS.MMM
    const match = assTime.match(/(\d+):(\d{2}):(\d{2})\.(\d{2})/);
    if (!match) return '00:00:00.000';
    const [, h, m, s, cs] = match;
    return `${h.padStart(2, '0')}:${m}:${s}.${cs}0`;
}

function guessLanguageFromExt(ext, filename) {
    const lower = filename.toLowerCase();
    if (lower.includes('.th') || lower.includes('thai')) return 'Thai';
    if (lower.includes('.en') || lower.includes('eng')) return 'English';
    if (lower.includes('.ja') || lower.includes('jpn')) return 'Japanese';
    if (lower.includes('.zh') || lower.includes('chi') || lower.includes('chs')) return 'Chinese';
    if (lower.includes('.ko') || lower.includes('kor')) return 'Korean';
    return '';
}

module.exports = {
    probeFile,
    findExternalSubtitles,
    srtToVtt,
    assToVtt,
    SUBTITLE_EXTENSIONS
};
