const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const VIDEO_EXTENSIONS = new Set([
  '.mp4', '.mkv', '.avi', '.mov', '.webm', '.m4v',
  '.wmv', '.flv', '.ts', '.m2ts', '.mpg', '.mpeg'
]);

/**
 * Generate a unique ID from a file path
 */
function generateId(filePath) {
  return crypto.createHash('md5').update(filePath).digest('hex').slice(0, 12);
}

/**
 * Format file size to human-readable string
 */
function formatSize(bytes) {
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let i = 0;
  let size = bytes;
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024;
    i++;
  }
  return `${size.toFixed(1)} ${units[i]}`;
}

/**
 * Scan a single directory recursively for video files
 */
function scanDirectory(dirPath, results = []) {
  try {
    const entries = fs.readdirSync(dirPath, { withFileTypes: true });
    
    for (const entry of entries) {
      // Skip hidden files/directories
      if (entry.name.startsWith('.')) continue;
      
      const fullPath = path.join(dirPath, entry.name);
      
      try {
        if (entry.isDirectory()) {
          scanDirectory(fullPath, results);
        } else if (entry.isFile()) {
          const ext = path.extname(entry.name).toLowerCase();
          if (VIDEO_EXTENSIONS.has(ext)) {
            const stat = fs.statSync(fullPath);
            results.push({
              id: generateId(fullPath),
              name: path.basename(entry.name, ext),
              filename: entry.name,
              path: fullPath,
              extension: ext.slice(1),
              size: stat.size,
              sizeFormatted: formatSize(stat.size),
              modified: stat.mtime.toISOString(),
              directory: path.dirname(fullPath)
            });
          }
        }
      } catch (err) {
        // Skip files we can't access
        console.warn(`Skipping ${fullPath}: ${err.message}`);
      }
    }
  } catch (err) {
    console.warn(`Cannot read directory ${dirPath}: ${err.message}`);
  }
  
  return results;
}

/**
 * Scan all configured video directories
 */
function scanAllDirectories(videoDirs) {
  const results = [];
  
  for (const dir of videoDirs) {
    const trimmed = dir.trim();
    if (trimmed && fs.existsSync(trimmed)) {
      scanDirectory(trimmed, results);
    } else if (trimmed) {
      console.warn(`Video directory does not exist: ${trimmed}`);
    }
  }
  
  // Sort by modified date (newest first)
  results.sort((a, b) => new Date(b.modified) - new Date(a.modified));
  
  return results;
}

/**
 * Find a video by its ID
 */
function findVideoById(videos, id) {
  return videos.find(v => v.id === id) || null;
}

module.exports = {
  scanAllDirectories,
  findVideoById,
  generateId,
  VIDEO_EXTENSIONS
};
