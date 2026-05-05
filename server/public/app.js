/**
 * Tv2Cast — Web UI Application
 * Video library browser & player
 */

// ──── State ────
let allVideos = [];
let filteredVideos = [];
let currentVideo = null;
let searchTimeout = null;

// Track selection state
let currentAudioTracks = [];
let currentSubtitleTracks = [];
let activeAudioIndex = -1;
let activeSubtitleIndex = -1;
let compatMode = false;

// ──── DOM Elements ────
const $videoContainer = document.getElementById('videoContainer');
const $searchInput = document.getElementById('searchInput');
const $videoCount = document.getElementById('videoCount');
const $serverInfo = document.getElementById('serverInfo');
const $playerOverlay = document.getElementById('playerOverlay');
const $playerTitle = document.getElementById('playerTitle');
const $videoPlayer = document.getElementById('videoPlayer');
const $btnClosePlayer = document.getElementById('btnClosePlayer');
const $btnRescan = document.getElementById('btnRescan');
const $toastContainer = document.getElementById('toastContainer');
const $btnAudioTrack = document.getElementById('btnAudioTrack');
const $btnSubtitleTrack = document.getElementById('btnSubtitleTrack');
const $btnCompatMode = document.getElementById('btnCompatMode');
const $trackModal = document.getElementById('trackModal');
const $trackModalTitle = document.getElementById('trackModalTitle');
const $trackModalBody = document.getElementById('trackModalBody');
const $btnCloseTrackModal = document.getElementById('btnCloseTrackModal');

// ──── API Helpers ────
const API_BASE = '';

async function fetchJSON(url) {
    try {
        const res = await fetch(API_BASE + url);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return await res.json();
    } catch (err) {
        console.error(`Fetch error for ${url}:`, err);
        throw err;
    }
}

// ──── Toast ────
function showToast(message, type = '') {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    $toastContainer.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(100px)';
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

// ──── Format Date ────
function formatDate(isoString) {
    const date = new Date(isoString);
    const now = new Date();
    const diff = now - date;

    if (diff < 60000) return 'Just now';
    if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
    if (diff < 604800000) return `${Math.floor(diff / 86400000)}d ago`;

    return date.toLocaleDateString('th-TH', {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
    });
}

// ──── Get Extension Color ────
function getExtColor(ext) {
    const colors = {
        mp4: '#4a9eff',
        mkv: '#34d399',
        avi: '#f87171',
        mov: '#fbbf24',
        webm: '#a78bfa',
        m4v: '#60a5fa',
        wmv: '#fb923c',
        flv: '#f472b6',
        ts: '#2dd4bf',
        mpg: '#c084fc',
        mpeg: '#c084fc'
    };
    return colors[ext] || '#8888a0';
}

// ──── Render Video Grid ────
function renderVideoGrid(videos) {
    if (videos.length === 0) {
        $videoContainer.innerHTML = `
      <div class="empty-state">
        <div class="empty-icon">🎬</div>
        <h2>No videos found</h2>
        <p>Add video files to your configured directories or adjust your search query.</p>
      </div>
    `;
        return;
    }

    const html = videos.map((video, i) => `
    <div class="video-card" data-id="${video.id}" onclick="playVideo('${video.id}')" style="animation-delay: ${Math.min(i * 0.03, 0.5)}s">
      <div class="card-thumbnail-wrapper">
        <div class="card-thumbnail" data-ext="${video.extension}">
          <span class="file-icon">🎬</span>
        </div>
        <div class="play-overlay">
          <div class="play-btn">▶</div>
        </div>
        <span class="card-badge" style="color: ${getExtColor(video.extension)}">${video.extension}</span>
      </div>
      <div class="card-body">
        <div class="card-title" title="${escapeHtml(video.filename)}">${escapeHtml(video.name)}</div>
        <div class="card-meta">
          <span>💾 ${video.sizeFormatted}</span>
          <span>📅 ${formatDate(video.modified)}</span>
        </div>
      </div>
    </div>
  `).join('');

    $videoContainer.innerHTML = `<div class="video-grid">${html}</div>`;
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// ──── Load Videos ────
async function loadVideos(rescan = false) {
    try {
        $videoContainer.innerHTML = `
      <div class="loading">
        <div class="spinner"></div>
        <span class="loading-text">Scanning video files...</span>
      </div>
    `;

        const url = rescan ? '/api/videos?rescan=true' : '/api/videos';
        const data = await fetchJSON(url);
        allVideos = data.videos;
        filteredVideos = allVideos;

        updateVideoCount(data.total);
        renderVideoGrid(filteredVideos);

        if (rescan) {
            showToast(`Found ${data.total} videos`, 'success');
        }
    } catch (err) {
        console.error('Failed to load videos:', err);
        $videoContainer.innerHTML = `
      <div class="empty-state">
        <div class="empty-icon">⚠️</div>
        <h2>Connection Error</h2>
        <p>Could not connect to the server. Make sure Tv2Cast is running.</p>
      </div>
    `;
    }
}

// ──── Load Server Info ────
async function loadServerInfo() {
    try {
        const info = await fetchJSON('/api/server-info');
        const lanAddr = info.addresses && info.addresses.length > 0
            ? info.addresses[0]
            : null;

        if (lanAddr) {
            const lanUrl = `http://${lanAddr.address}:${info.port}`;
            $serverInfo.innerHTML = `
        <div class="server-info">
          <div class="status-dot"></div>
          <div class="info-text">
            <strong>Tv2Cast Server</strong> is running<br>
            <small>Access from other devices on your network:</small>
          </div>
          <div class="lan-url" onclick="copyToClipboard('${lanUrl}')" title="Click to copy">${lanUrl}</div>
        </div>
      `;
        }
    } catch (err) {
        console.warn('Could not load server info:', err);
    }
}

// ──── Update Video Count ────
function updateVideoCount(count) {
    $videoCount.textContent = `${count} video${count !== 1 ? 's' : ''}`;
}

// ──── Search ────
function handleSearch(query) {
    const q = query.toLowerCase().trim();

    if (!q) {
        filteredVideos = allVideos;
    } else {
        filteredVideos = allVideos.filter(v =>
            v.name.toLowerCase().includes(q) ||
            v.filename.toLowerCase().includes(q) ||
            v.directory.toLowerCase().includes(q) ||
            v.extension.toLowerCase().includes(q)
        );
    }

    updateVideoCount(filteredVideos.length);
    renderVideoGrid(filteredVideos);
}

// ──── Play Video ────
function playVideo(videoId) {
    const video = allVideos.find(v => v.id === videoId);
    if (!video) return;

    currentVideo = video;
    $playerTitle.textContent = video.name;

    const streamUrl = compatMode
        ? `/api/videos/${video.id}/compat-stream`
        : `/api/videos/${video.id}/stream`;

    $videoPlayer.src = streamUrl;
    $playerOverlay.classList.add('active');
    document.body.style.overflow = 'hidden';

    // Update button UI
    if (compatMode) {
        $btnCompatMode.classList.add('active');
    } else {
        $btnCompatMode.classList.remove('active');
    }

    // Reset track state
    clearTracks();

    // Auto-play
    $videoPlayer.play().catch(() => { });

    // Load tracks asynchronously
    loadTracks(video.id);
}

// ──── Close Player ────
function closePlayer() {
    $videoPlayer.pause();
    $videoPlayer.src = '';
    $playerOverlay.classList.remove('active');
    document.body.style.overflow = '';
    currentVideo = null;
    clearTracks();
    closeTrackModal();
}

// ──── Track System ────
function clearTracks() {
    currentAudioTracks = [];
    currentSubtitleTracks = [];
    activeAudioIndex = -1;
    activeSubtitleIndex = -1;

    // Remove all <track> elements
    const tracks = $videoPlayer.querySelectorAll('track');
    tracks.forEach(t => t.remove());

    $btnAudioTrack.classList.remove('has-tracks');
    $btnSubtitleTrack.classList.remove('has-tracks');
    $trackModalBody.innerHTML = '';
}

async function loadTracks(videoId) {
    try {
        const data = await fetchJSON(`/api/videos/${videoId}/tracks`);
        currentAudioTracks = data.audioTracks || [];
        currentSubtitleTracks = data.subtitleTracks || [];

        if (currentAudioTracks.length > 1) {
            $btnAudioTrack.classList.add('has-tracks');
        }

        if (currentSubtitleTracks.length > 0) {
            $btnSubtitleTrack.classList.add('has-tracks');
        }
    } catch (err) {
        console.warn('Could not load tracks:', err);
    }
}

function openTrackModal(type) {
    $trackModalTitle.textContent = type === 'audio' ? 'Select Audio Language' : 'Select Subtitles';

    let html = '';
    if (type === 'audio') {
        if (currentAudioTracks.length === 0) {
            html = '<div class="track-section-title">No audio tracks detected</div>';
        } else {
            html = '<div class="track-section-title">Audio Tracks</div>';
            currentAudioTracks.forEach((track, i) => {
                const isActive = i === activeAudioIndex || (activeAudioIndex === -1 && track.default);
                if (isActive) activeAudioIndex = i;
                const channels = track.channels ? `${track.channels}ch` : '';
                html += `
                    <div class="track-item ${isActive ? 'active' : ''}" onclick="selectAudioTrack(${i})">
                        <div class="check">${isActive ? '✓' : ''}</div>
                        <div class="label">${escapeHtml(track.label)}</div>
                        <div class="lang">${track.language} ${channels}</div>
                    </div>
                `;
            });
        }
    } else {
        html = '<div class="track-section-title">Subtitles</div>';
        // Off option
        const isOff = activeSubtitleIndex === -1;
        html += `
            <div class="track-item ${isOff ? 'active' : ''}" onclick="selectSubtitleTrack(-1)">
                <div class="check">${isOff ? '✓' : ''}</div>
                <div class="label">Off</div>
            </div>
        `;

        if (currentSubtitleTracks.length > 0) {
            html += '<div class="track-divider"></div>';
            currentSubtitleTracks.forEach((track, i) => {
                const isActive = i === activeSubtitleIndex;
                const source = track.embedded ? '📦' : '📄';
                html += `
                    <div class="track-item ${isActive ? 'active' : ''}" onclick="selectSubtitleTrack(${i})">
                        <div class="check">${isActive ? '✓' : ''}</div>
                        <div class="label">${source} ${escapeHtml(track.label)}</div>
                        <div class="lang">${track.language}</div>
                    </div>
                `;
            });
        }
    }

    $trackModalBody.innerHTML = html;
    $trackModal.classList.add('active');
}

function closeTrackModal() {
    $trackModal.classList.remove('active');
}

function selectAudioTrack(index) {
    activeAudioIndex = index;
    const track = currentAudioTracks[index];

    if ($videoPlayer.audioTracks && $videoPlayer.audioTracks.length > 1) {
        for (let i = 0; i < $videoPlayer.audioTracks.length; i++) {
            $videoPlayer.audioTracks[i].enabled = (i === index);
        }
        showToast(`🔊 Audio: ${track.label}`, 'success');
    } else {
        showToast(`🔊 Audio: ${track.label} (Browser limiting switching)`);
    }

    closeTrackModal();
}

function selectSubtitleTrack(index) {
    // Remove existing
    const existingTracks = $videoPlayer.querySelectorAll('track');
    existingTracks.forEach(t => {
        if (t.src && t.src.startsWith('blob:')) URL.revokeObjectURL(t.src);
        t.remove();
    });

    for (let i = 0; i < $videoPlayer.textTracks.length; i++) {
        $videoPlayer.textTracks[i].mode = 'disabled';
    }

    if (index === -1) {
        activeSubtitleIndex = -1;
        showToast('💬 Subtitles: Off', 'success');
    } else {
        activeSubtitleIndex = index;
        const sub = currentSubtitleTracks[index];
        const subUrl = `/api/videos/${currentVideo.id}/subtitle/${sub.index}`;

        fetch(subUrl)
            .then(res => res.text())
            .then(vtt => {
                const blob = new Blob([vtt], { type: 'text/vtt' });
                const url = URL.createObjectURL(blob);
                const trackEl = document.createElement('track');
                trackEl.kind = 'subtitles';
                trackEl.label = sub.label;
                trackEl.srclang = sub.language || 'und';
                trackEl.src = url;
                trackEl.default = true;
                $videoPlayer.appendChild(trackEl);

                const activate = () => {
                    for (let i = 0; i < $videoPlayer.textTracks.length; i++) {
                        $videoPlayer.textTracks[i].mode = 'showing';
                    }
                };
                activate();
                setTimeout(activate, 100);
            })
            .catch(err => showToast(`❌ Error: ${err.message}`));

        showToast(`💬 Subtitle: ${sub.label}`, 'success');
    }

    closeTrackModal();
}

// ──── Copy to Clipboard ────
async function copyToClipboard(text) {
    try {
        await navigator.clipboard.writeText(text);
        showToast('📋 Copied to clipboard!', 'success');
    } catch {
        showToast('Could not copy to clipboard');
    }
}

// ──── Event Listeners ────
$searchInput.addEventListener('input', (e) => {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(() => handleSearch(e.target.value), 200);
});

$btnClosePlayer.addEventListener('click', closePlayer);

$playerOverlay.addEventListener('click', (e) => {
    if (e.target === $playerOverlay) closePlayer();
});

$btnRescan.addEventListener('click', () => {
    loadVideos(true);
});

$btnAudioTrack.addEventListener('click', () => openTrackModal('audio'));
$btnSubtitleTrack.addEventListener('click', () => openTrackModal('subtitle'));

$btnCompatMode.addEventListener('click', () => {
    compatMode = !compatMode;

    if (currentVideo) {
        const currentTime = $videoPlayer.currentTime;
        const isPaused = $videoPlayer.paused;

        // Update URL
        const streamUrl = compatMode
            ? `/api/videos/${currentVideo.id}/compat-stream?t=${Date.now()}`
            : `/api/videos/${currentVideo.id}/stream?t=${Date.now()}`;

        $videoPlayer.src = streamUrl;
        $videoPlayer.currentTime = currentTime;

        if (!isPaused) {
            $videoPlayer.play().catch(() => { });
        }

        showToast(compatMode ? '🚀 Fast Mode: ON (720p Transcode)' : '💎 Original Mode: ON', 'success');
    }

    if (compatMode) {
        $btnCompatMode.classList.add('active');
    } else {
        $btnCompatMode.classList.remove('active');
    }
});

$btnCloseTrackModal.addEventListener('click', closeTrackModal);
$trackModal.addEventListener('click', (e) => {
    if (e.target === $trackModal) closeTrackModal();
});

// Keyboard shortcuts
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        if ($trackModal.classList.contains('active')) {
            closeTrackModal();
        } else if ($playerOverlay.classList.contains('active')) {
            closePlayer();
        }
    }

    if ((e.key === 'k' && (e.metaKey || e.ctrlKey)) || (e.key === '/' && document.activeElement !== $searchInput)) {
        e.preventDefault();
        $searchInput.focus();
    }
});

// Handle video errors
$videoPlayer.addEventListener('error', () => {
    if (currentVideo) {
        showToast(`Cannot play: ${currentVideo.filename}. Format support issue.`);
    }
});

// ──── Initialize ────
document.addEventListener('DOMContentLoaded', () => {
    loadServerInfo();
    loadVideos();
});
