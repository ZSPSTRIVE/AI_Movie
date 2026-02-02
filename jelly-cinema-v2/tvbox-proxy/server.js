const express = require('express');
const axios = require('axios');
const cors = require('cors');
const { XMLParser } = require('fast-xml-parser');
const http = require('http');
const https = require('https');

const app = express();
const PORT = process.env.PORT || 3001;

app.use(cors());
app.use(express.json());

const TV_SOURCES = [
    // TVBox配置源
    'https://6800.kstore.vip/fish.json',
    'http://cdn.qiaoji8.com/tvbox.json',
];

const db = require('./services/db');

// 直接采集API列表（默认/Fallback）
const DEFAULT_API_SOURCES = [
    // 稳定可靠的资源站
    { name: '量子资源', api: 'https://cj.lziapi.com/api.php/provide/vod/', type: 'json', priority: 1 },
    { name: '非凡资源', api: 'https://cj.ffzyapi.com/api.php/provide/vod/', type: 'json', priority: 2 },
    { name: '红牛资源', api: 'https://www.hongniuzy2.com/api.php/provide/vod/', type: 'json', priority: 3 },
    { name: '新浪资源', api: 'https://api.xinlangapi.com/xinlangapi.php/provide/vod/', type: 'json', priority: 5 },
    { name: '光速资源', api: 'https://api.guangsuapi.com/api.php/provide/vod/', type: 'json', priority: 6 },
];

let sourcesCache = null;
let sourcesCacheTime = 0;
const SOURCES_CACHE_TTL = 60 * 1000; // 1分钟缓存

// 启动时清除缓存并打印源信息
console.log('=== TVBox Proxy Starting ===');
console.log('Default API sources:');
DEFAULT_API_SOURCES.forEach(s => console.log(`  - ${s.name}: ${s.api}`));

async function getApiSources() {
    // 检查缓存
    if (sourcesCache && Date.now() - sourcesCacheTime < SOURCES_CACHE_TTL) {
        return sourcesCache;
    }

    // 已知失效的域名（404/403/超时）
    const invalidDomains = [
        'ysso.cc',
        'yunpan6.com',
        'mqtv.cc',
        'czzymovie.com',
        '5weiting.com',
        'film.symx.club',
        'dy.jmzp.net.cn',
        // 这些是格式错误的多URL拼接
        'y2s52n7.com',
    ];

    // 验证URL是否有效
    function isValidApiUrl(url) {
        if (!url || typeof url !== 'string') return false;
        // 检查是否包含逗号（多URL拼接错误）
        if (url.includes(',')) {
            console.warn('Skipping malformed URL (contains comma):', url.substring(0, 50));
            return false;
        }
        // 必须是有效的HTTP(S) URL
        if (!url.startsWith('http://') && !url.startsWith('https://')) {
            console.warn('Skipping invalid URL (no protocol):', url.substring(0, 50));
            return false;
        }
        // 检查已知失效域名
        for (const domain of invalidDomains) {
            if (url.includes(domain)) {
                console.warn('Skipping known invalid domain:', domain);
                return false;
            }
        }
        return true;
    }

    try {
        // 尝试从数据库加载
        const rows = await db.query('SELECT * FROM t_tvbox_source WHERE enabled = 1 ORDER BY priority ASC');
        if (rows && rows.length > 0) {
            const validSources = rows
                .filter(row => isValidApiUrl(row.api_url))
                .map(row => ({
                    name: row.source_name,
                    api: row.api_url,
                    type: row.api_type || 'json',
                    priority: row.priority
                }));

            if (validSources.length > 0) {
                sourcesCache = validSources;
                sourcesCacheTime = Date.now();
                console.log(`Loaded ${validSources.length} valid sources from DB (filtered from ${rows.length} total)`);
                return sourcesCache;
            }
        }
    } catch (e) {
        console.error('Failed to load sources from DB, using fallback:', e.message);
    }

    // DB失败或为空时使用默认列表
    return DEFAULT_API_SOURCES;
}

const PLACEHOLDER_COVER = 'https://via.placeholder.com/400x600?text=No+Image';
const CACHE_TTL_MS = 10 * 60 * 1000;
const LIST_CACHE_TTL_MS = 5 * 60 * 1000;
const REQUEST_TIMEOUT_MS = 10000;

const xmlParser = new XMLParser({
    ignoreAttributes: false,
    attributeNamePrefix: '',
    allowBooleanAttributes: true,
});

const cacheStore = new Map();
const filmIndex = new Map();
const siteApiCache = new Map();

const axiosClient = axios.create({
    httpAgent: new http.Agent({ keepAlive: true, maxSockets: 128 }),
    httpsAgent: new https.Agent({ keepAlive: true, maxSockets: 128 }),
});

function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

function isRetryableAxiosError(error) {
    const code = error?.code;
    const message = String(error?.message || '').toLowerCase();
    if (code && ['ECONNRESET', 'ETIMEDOUT', 'ECONNABORTED', 'EAI_AGAIN', 'ENOTFOUND', 'EPIPE'].includes(code)) {
        return true;
    }
    if (message.includes('socket disconnected') || message.includes('tls connection was established') || message.includes('network socket disconnected')) {
        return true;
    }
    const status = error?.response?.status;
    if (status && [429, 500, 502, 503, 504].includes(status)) {
        return true;
    }
    return false;
}

async function axiosGetWithRetry(url, config, options) {
    const retries = options?.retries ?? 2;
    const baseDelayMs = options?.baseDelayMs ?? 350;

    let lastError;
    for (let attempt = 0; attempt <= retries; attempt++) {
        try {
            return await axiosClient.get(url, config);
        } catch (error) {
            lastError = error;
            if (attempt >= retries || !isRetryableAxiosError(error)) {
                throw error;
            }
            const delay = baseDelayMs * Math.pow(2, attempt) + Math.floor(Math.random() * 120);
            await sleep(delay);
        }
    }
    throw lastError;
}

function getCache(key) {
    const entry = cacheStore.get(key);
    if (!entry) return null;
    if (Date.now() > entry.expiresAt) {
        cacheStore.delete(key);
        return null;
    }
    return entry.value;
}

function setCache(key, value, ttlMs) {
    cacheStore.set(key, { value, expiresAt: Date.now() + ttlMs });
}

function isHttpUrl(value) {
    return typeof value === 'string' && /^https?:\/\//i.test(value.trim());
}

function normalizeUrl(value) {
    if (typeof value !== 'string') return '';
    const trimmed = value.trim();
    if (trimmed.startsWith('//')) return `https:${trimmed}`;
    return trimmed;
}

function buildUrl(baseUrl, params) {
    try {
        const url = new URL(baseUrl);
        Object.entries(params).forEach(([key, value]) => {
            if (value === undefined || value === null || value === '') return;
            url.searchParams.set(key, value);
        });
        return url.toString();
    } catch (error) {
        return baseUrl;
    }
}

function toBase64Url(value) {
    return Buffer.from(value, 'utf8')
        .toString('base64')
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=+$/g, '');
}

function fromBase64Url(value) {
    const padded = value.replace(/-/g, '+').replace(/_/g, '/');
    const padLength = (4 - (padded.length % 4)) % 4;
    const normalized = padded + '='.repeat(padLength);
    return Buffer.from(normalized, 'base64').toString('utf8');
}

function buildFilmId(siteKey, vodId) {
    return toBase64Url(`${siteKey}|${vodId}`);
}

function parseFilmId(value) {
    try {
        const decoded = fromBase64Url(value);
        const [siteKey, vodId] = decoded.split('|');
        if (!siteKey || !vodId) return null;
        return { siteKey, vodId };
    } catch (error) {
        return null;
    }
}

function pickField(obj, keys) {
    if (!obj) return '';
    for (const key of keys) {
        const value = obj[key];
        if (value !== undefined && value !== null && value !== '') {
            return value;
        }
    }
    return '';
}

function safeNumber(value, fallback) {
    const num = Number(value);
    return Number.isFinite(num) ? num : fallback;
}

function extractYear(value) {
    if (!value) return new Date().getFullYear();
    const match = String(value).match(/\d{4}/);
    return match ? Number(match[0]) : new Date().getFullYear();
}

function generateRating() {
    return Number((Math.random() * 2 + 7).toFixed(1));
}

function generatePlayCount() {
    return Math.floor(Math.random() * 100000);
}

/**
 * 解析 vod_play_url 格式的播放链接
 * 格式: "剧集名1$url1#剧集名2$url2$$$剧集名1$url1#剧集名2$url2"
 * 多个播放源用 $$$ 分隔，每个源内的剧集用 # 分隔
 */
function parseEpisodes(playUrl, playFrom) {
    if (!playUrl) return [];

    const sources = playUrl.split('$$$');
    const sourceNames = playFrom ? playFrom.split('$$$') : [];
    const episodes = [];

    for (let i = 0; i < sources.length; i++) {
        const sourceName = sourceNames[i] || `源${i + 1}`;
        const episodeList = sources[i].split('#');

        for (const ep of episodeList) {
            const parts = ep.split('$');
            if (parts.length >= 2) {
                const name = parts[0];
                const url = parts[1];
                if (url && (url.startsWith('http') || url.startsWith('//'))) {
                    episodes.push({
                        name: name,
                        url: url.startsWith('//') ? 'https:' + url : url,
                        source: sourceName,
                        // 标记是否为 m3u8 格式
                        isM3u8: url.includes('.m3u8') || sourceName.toLowerCase().includes('m3u8'),
                    });
                }
            }
        }
    }

    return episodes;
}

async function fetchRemote(url) {
    try {
        const response = await axiosGetWithRetry(url, {
            timeout: REQUEST_TIMEOUT_MS,
            responseType: 'arraybuffer',
            headers: {
                'User-Agent': 'okhttp/4.10.0',
                'Accept': 'application/json,text/plain,*/*',
            },
            validateStatus: status => status >= 200 && status < 400,
        }, { retries: 2 });
        return Buffer.from(response.data).toString('utf8');
    } catch (error) {
        console.error(`Failed to fetch ${url}:`, error.message);
        return null;
    }
}

function parseJsonBlock(rawText) {
    if (typeof rawText !== 'string') return rawText;
    const trimmed = rawText.trim();
    if (!trimmed) return null;
    try {
        return JSON.parse(trimmed);
    } catch (error) {
        const start = trimmed.indexOf('{');
        const end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            const block = trimmed.slice(start, end + 1);
            try {
                return JSON.parse(block);
            } catch (innerError) {
                return null;
            }
        }
        return null;
    }
}

function parseResponseBody(rawBody) {
    if (rawBody === null || rawBody === undefined) return null;
    if (typeof rawBody === 'object') return rawBody;
    const text = String(rawBody).trim();
    if (!text) return null;
    const json = parseJsonBlock(text);
    if (json) return json;
    try {
        return xmlParser.parse(text);
    } catch (error) {
        return null;
    }
}

async function fetchTVBoxSource(url) {
    const cacheKey = `tvbox:${url}`;
    const cached = getCache(cacheKey);
    if (cached) return cached;
    const raw = await fetchRemote(url);
    const data = parseJsonBlock(raw);
    if (data) {
        setCache(cacheKey, data, CACHE_TTL_MS);
    }
    return data;
}

function normalizeSite(site, sourceUrl, index) {
    const key = String(site.key || site.name || `${sourceUrl}-${index}`);
    return {
        key,
        name: site.name || site.key || key,
        type: site.type ?? 0,
        api: normalizeUrl(site.api || ''),
        ext: site.ext,
        searchable: site.searchable !== 0,
        sourceUrl,
    };
}

async function getAllSites() {
    const cacheKey = 'tvbox:sites';
    const cached = getCache(cacheKey);
    if (cached) return cached;

    // 已知无效的域名或 URL 模式
    const invalidPatterns = [
        'ysso.cc',
        'yunpan6.com',
        'mqtv.cc',
        'czzymovie.com',
        '5weiting.com',
        'film.symx.club',
        'dy.jmzp.net.cn',
        'y2s52n7.com',
    ];

    // 验证站点是否有效
    function isValidSite(site) {
        const api = site.api || site.ext || '';
        if (!api) return false;
        // 检查 URL 是否包含逗号（格式错误）
        if (api.includes(',')) {
            console.warn('Skipping malformed site URL (contains comma):', api.substring(0, 50));
            return false;
        }
        // 检查已知无效域名
        for (const pattern of invalidPatterns) {
            if (api.includes(pattern)) {
                console.warn('Skipping known invalid site:', pattern);
                return false;
            }
        }
        return true;
    }

    const results = await Promise.allSettled(
        TV_SOURCES.map(async (sourceUrl) => {
            const data = await fetchTVBoxSource(sourceUrl);
            if (!data || !Array.isArray(data.sites)) return [];
            return data.sites.map((site, index) => normalizeSite(site, sourceUrl, index));
        })
    );

    const sites = [];
    const seen = new Set();
    let skippedCount = 0;
    for (const result of results) {
        if (result.status !== 'fulfilled') continue;
        for (const site of result.value) {
            // 过滤无效站点
            if (!isValidSite(site)) {
                skippedCount++;
                continue;
            }
            const dedupeKey = `${site.key}|${site.api}|${site.type}`;
            if (seen.has(dedupeKey)) continue;
            seen.add(dedupeKey);
            sites.push(site);
        }
    }

    console.log(`Loaded ${sites.length} valid sites (skipped ${skippedCount} invalid)`);
    setCache(cacheKey, sites, CACHE_TTL_MS);
    return sites;
}

function buildApiCandidates(site) {
    // 已知无效的域名或 URL 模式
    const invalidPatterns = [
        'ysso.cc', 'yunpan6.com', 'mqtv.cc', 'czzymovie.com',
        '5weiting.com', 'film.symx.club', 'dy.jmzp.net.cn', 'y2s52n7.com',
    ];

    // 验证 URL 是否有效
    function isValidUrl(url) {
        if (!url || typeof url !== 'string') return false;
        if (url.includes(',')) return false; // 格式错误
        for (const pattern of invalidPatterns) {
            if (url.includes(pattern)) return false;
        }
        return true;
    }

    const candidates = [];
    if (isHttpUrl(site.api) && isValidUrl(site.api)) {
        candidates.push(normalizeUrl(site.api));
    }
    if (typeof site.ext === 'string' && isHttpUrl(site.ext) && isValidUrl(site.ext)) {
        const extUrl = normalizeUrl(site.ext);
        candidates.push(extUrl);
        const trimmed = extUrl.replace(/\/+$/g, '');
        candidates.push(`${trimmed}/api.php/provide/vod/`);
        candidates.push(`${trimmed}/api.php/provide/vod`);
        candidates.push(`${trimmed}/api.php/provide/vod/?`);
    }
    return [...new Set(candidates)];
}

function resolveSiteApi(site) {
    if (siteApiCache.has(site.key)) {
        return siteApiCache.get(site.key) || '';
    }
    return '';
}

function extractListFromXml(parsed) {
    const rss = parsed.rss || parsed;
    const listNode = rss.list || rss.channel?.list || rss.channel;
    const videos = listNode?.video || listNode?.vod || rss.video || rss.vod;
    const list = Array.isArray(videos) ? videos : videos ? [videos] : [];
    const total = safeNumber(
        listNode?.recordcount || listNode?.recordCount || listNode?.total || listNode?.records,
        list.length
    );
    return { list, total };
}

function extractListFromJson(parsed) {
    if (!parsed || typeof parsed !== 'object') return { list: [], total: 0 };
    if (Array.isArray(parsed.list)) {
        return { list: parsed.list, total: safeNumber(parsed.total, parsed.list.length) };
    }
    if (parsed.data && Array.isArray(parsed.data.list)) {
        return { list: parsed.data.list, total: safeNumber(parsed.data.total, parsed.data.list.length) };
    }
    if (Array.isArray(parsed.data)) {
        return { list: parsed.data, total: safeNumber(parsed.total, parsed.data.length) };
    }
    if (parsed.result && Array.isArray(parsed.result.list)) {
        return { list: parsed.result.list, total: safeNumber(parsed.result.total, parsed.result.list.length) };
    }
    if (parsed.rss) {
        return extractListFromXml(parsed);
    }
    return { list: [], total: 0 };
}

function extractVodList(parsed) {
    if (!parsed) return { list: [], total: 0 };
    if (parsed.rss || parsed.list || parsed.data || parsed.result) {
        return extractListFromJson(parsed);
    }
    return extractListFromXml(parsed);
}

function buildFilmFromVod(vod, site) {
    const vodIdRaw = pickField(vod, [
        'vod_id',
        'id',
        'vodid',
        'vodId',
        'ID',
        '_id',
        'sid',
    ]);
    const title = String(pickField(vod, ['vod_name', 'name', 'title']) || site.name || 'Unknown');
    const vodId = vodIdRaw ? String(vodIdRaw) : title;
    const filmId = buildFilmId(site.key, vodId);

    const coverUrl = pickField(vod, ['vod_pic', 'pic', 'cover', 'img', 'image']) || PLACEHOLDER_COVER;
    const description = String(pickField(vod, ['vod_content', 'content', 'desc', 'vod_remarks', 'note']) || '');
    const year = extractYear(pickField(vod, ['vod_year', 'year', 'dt', 'pubdate']));
    const rating = safeNumber(pickField(vod, ['vod_score', 'score', 'rating']), generateRating());
    const playCount = safeNumber(pickField(vod, ['vod_hits', 'hits', 'playCount']), generatePlayCount());
    const director = String(pickField(vod, ['vod_director', 'director']) || '');
    const actors = String(pickField(vod, ['vod_actor', 'actor']) || '');
    const region = String(pickField(vod, ['vod_area', 'area']) || '');
    const duration = safeNumber(pickField(vod, ['vod_duration', 'duration']), 0);

    const film = {
        id: filmId,
        title,
        coverUrl,
        description,
        year,
        rating,
        playCount,
        isVip: false,
        director,
        actors,
        region,
        duration,
        sourceKey: site.key,
        sourceName: site.name,
    };

    filmIndex.set(filmId, { siteKey: site.key, vodId, film });
    return film;
}

async function fetchSiteVodList(site, options = {}) {
    const cachedApi = resolveSiteApi(site);
    const candidates = cachedApi ? [cachedApi] : buildApiCandidates(site);
    if (!candidates.length) {
        siteApiCache.set(site.key, '');
        return { list: [], total: 0 };
    }

    const { page = 1, pageSize = 20, keyword, ids } = options;
    const query = {
        ac: 'detail',
        pg: page,
        wd: keyword,
        ids,
        ps: pageSize,
        pagesize: pageSize,
        limit: pageSize,
    };
    let lastResult = { list: [], total: 0 };

    for (const apiUrl of candidates) {
        const url = buildUrl(apiUrl, query);
        const raw = await fetchRemote(url);
        const parsed = parseResponseBody(raw);
        const { list, total } = extractVodList(parsed);
        lastResult = { list, total };
        if (list.length || total) {
            siteApiCache.set(site.key, apiUrl);
            return lastResult;
        }
    }

    siteApiCache.set(site.key, '');

    return lastResult;
}

async function fetchSiteFilms(site, options = {}) {
    const cacheKey = `list:${site.key}:${options.page || 1}:${options.pageSize || 20}:${options.keyword || ''}`;
    const cached = getCache(cacheKey);
    if (cached) return cached;

    const { list, total } = await fetchSiteVodList(site, options);
    const films = list.map((vod) => buildFilmFromVod(vod, site));
    const result = { films, total };
    setCache(cacheKey, result, LIST_CACHE_TTL_MS);
    return result;
}

async function fetchSiteDetail(site, vodId) {
    const cacheKey = `detail:${site.key}:${vodId}`;
    const cached = getCache(cacheKey);
    if (cached) return cached;

    const { list } = await fetchSiteVodList(site, { ids: vodId, page: 1, pageSize: 1 });
    if (!list.length) return null;
    const vod = list[0];
    const film = buildFilmFromVod(vod, site);
    const result = { film, vod };
    setCache(cacheKey, result, LIST_CACHE_TTL_MS);
    return result;
}

function parsePlayInfo(vod) {
    const playFromRaw = pickField(vod, ['vod_play_from', 'play_from', 'source']);
    const playUrlRaw = pickField(vod, ['vod_play_url', 'play_url', 'url', 'playUrl']);

    console.log('Raw play_from:', playFromRaw?.substring?.(0, 100) || playFromRaw);
    console.log('Raw play_url:', playUrlRaw?.substring?.(0, 300) || playUrlRaw);

    if (!playUrlRaw) {
        return { playUrl: '', episodes: [] };
    }

    const fromList = playFromRaw ? String(playFromRaw).split('$$$') : [];
    const groups = String(playUrlRaw).split('$$$');
    const episodes = [];

    groups.forEach((group, groupIndex) => {
        const sourceName = fromList[groupIndex] || `线路${groupIndex + 1}`;
        const items = group.split('#');
        items.forEach((item, itemIndex) => {
            if (!item) return;
            const [namePart, urlPart] = item.split('$');
            let url = (urlPart || namePart || '').trim();
            if (!url) return;

            // 只接受有效的http/https视频链接
            if (!url.startsWith('http://') && !url.startsWith('https://')) {
                // 尝试base64解码
                try {
                    const decoded = Buffer.from(url, 'base64').toString('utf8');
                    if (decoded.startsWith('http://') || decoded.startsWith('https://')) {
                        url = decoded;
                    } else {
                        // 不是有效链接，跳过这个源
                        console.log('Skipping encrypted URL:', url.substring(0, 30));
                        return;
                    }
                } catch (e) {
                    console.log('Skipping invalid URL:', url.substring(0, 30));
                    return;
                }
            }

            const episodeName = urlPart ? (namePart || `第${itemIndex + 1}集`) : `第${itemIndex + 1}集`;
            const label = fromList.length > 1 ? `${sourceName} - ${episodeName}` : episodeName;
            episodes.push({ name: label, url });
        });
    });

    // 打印第一个解析出的URL
    if (episodes.length > 0) {
        console.log('First valid episode URL:', episodes[0].url?.substring?.(0, 100));
    } else {
        console.log('No valid playable URLs found');
    }

    // 被封锁的 CDN 域名列表（这些域名返回 403 或无法播放）
    const blockedDomains = [
        'xluuss.com',
        'gsuus.com',
        'ffzy-play',
        'ffzy-online',
        'ffzyread',
        'svipsvip',
        '/share/',  // /share/ 间接链接通常也有问题
    ];

    // 检查 URL 是否来自被封锁的域名
    function isBlockedUrl(url) {
        if (!url) return true;
        for (const blocked of blockedDomains) {
            if (url.includes(blocked)) return true;
        }
        return false;
    }

    // 重新排序 episodes：把未封锁的放前面，封锁的放后面
    const unblockedEpisodes = episodes.filter(ep => ep.url && !isBlockedUrl(ep.url));
    const blockedEpisodes = episodes.filter(ep => ep.url && isBlockedUrl(ep.url));
    const reorderedEpisodes = [...unblockedEpisodes, ...blockedEpisodes];

    console.log(`Episodes reordered: ${unblockedEpisodes.length} unblocked, ${blockedEpisodes.length} blocked`);

    // 优先选择 m3u8 直链，跳过被封锁的域名
    let bestUrl = '';

    // 第一优先级：包含 .m3u8 且不在封锁列表中
    for (const ep of reorderedEpisodes) {
        if (!ep.url) continue;
        if (isBlockedUrl(ep.url)) continue;
        if (ep.url.includes('.m3u8') || ep.url.includes('index.m3u8')) {
            bestUrl = ep.url;
            console.log('Selected URL (priority 1 - unblocked m3u8):', ep.url.substring(0, 80));
            break;
        }
    }

    // 第二优先级：任何不在封锁列表中的链接
    if (!bestUrl) {
        for (const ep of reorderedEpisodes) {
            if (!ep.url) continue;
            if (!isBlockedUrl(ep.url)) {
                bestUrl = ep.url;
                console.log('Selected URL (priority 2 - unblocked):', ep.url.substring(0, 80));
                break;
            }
        }
    }

    // 第三优先级：封锁列表中的 m3u8（备选）
    if (!bestUrl) {
        for (const ep of reorderedEpisodes) {
            if (!ep.url) continue;
            if (ep.url.includes('.m3u8')) {
                bestUrl = ep.url;
                console.log('Selected URL (priority 3 - blocked m3u8):', ep.url.substring(0, 80));
                break;
            }
        }
    }

    // 最后：任何可用的链接
    if (!bestUrl && reorderedEpisodes.length > 0) {
        bestUrl = reorderedEpisodes[0].url;
        console.log('Selected URL (fallback):', bestUrl?.substring(0, 80));
    }

    return { playUrl: bestUrl, episodes: reorderedEpisodes };
}

function isM3u8Url(url) {
    if (!url) return false;
    const s = String(url);
    return s.includes('.m3u8') || s.includes('index.m3u8');
}

function pickShareReferer(episodes) {
    if (!Array.isArray(episodes)) return null;
    for (const ep of episodes) {
        const u = ep?.url;
        if (typeof u === 'string' && u.includes('/share/')) {
            return u;
        }
    }
    return null;
}

function buildM3u8ProxyUrl(m3u8Url, referer) {
    const base = `http://localhost:${PORT}`;
    const refererQuery = referer ? `&referer=${encodeURIComponent(referer)}` : '';
    return `${base}/api/tvbox/m3u8?url=${encodeURIComponent(m3u8Url)}${refererQuery}`;
}

function dedupeFilms(list) {
    const seen = new Set();
    const deduped = [];
    for (const film of list) {
        const key = film.id || film.title;
        if (seen.has(key)) continue;
        seen.add(key);
        deduped.push(film);
    }
    return deduped;
}

function shuffle(list) {
    const arr = list.slice();
    for (let i = arr.length - 1; i > 0; i -= 1) {
        const j = Math.floor(Math.random() * (i + 1));
        [arr[i], arr[j]] = [arr[j], arr[i]];
    }
    return arr;
}

async function aggregateFilms({ page, pageSize, keyword, limit, onlySearchable }) {
    const sites = await getAllSites();
    const supportedSites = sites.filter((site) => {
        const cachedApi = resolveSiteApi(site);
        const candidates = cachedApi ? [cachedApi] : buildApiCandidates(site);
        if (!candidates.length) return false;
        if (onlySearchable && !site.searchable) return false;
        return true;
    });

    const results = await Promise.allSettled(
        supportedSites.map((site) => fetchSiteFilms(site, { page, pageSize, keyword }))
    );

    let total = 0;
    let films = [];

    results.forEach((result) => {
        if (result.status !== 'fulfilled') return;
        const { films: siteFilms, total: siteTotal } = result.value;
        films = films.concat(siteFilms);
        total += siteTotal || 0;
    });

    films = dedupeFilms(films);
    if (limit) {
        films = shuffle(films).slice(0, limit);
    }

    return { films, total };
}

// 从直接API源获取电影数据
async function fetchFromDirectApi(source) {
    try {
        const url = `${source.api}?ac=detail&pg=1`;
        console.log('Fetching from direct API:', source.name);
        const response = await axiosGetWithRetry(url, {
            timeout: REQUEST_TIMEOUT_MS + 5000,
            headers: { 'User-Agent': 'okhttp/4.10.0' },
        }, { retries: 2 });
        const data = response.data;
        const list = data.list || [];

        return list.map((vod, idx) => {
            const filmId = `${source.name}|${vod.vod_id}`;
            const encodedId = Buffer.from(filmId).toString('base64').replace(/\+/g, '-').replace(/\//g, '_');

            // 存储到索引
            filmIndex.set(encodedId, {
                siteKey: source.name,
                vodId: vod.vod_id,
                apiUrl: source.api,
                vod: vod
            });

            return {
                id: encodedId,
                title: vod.vod_name || 'Unknown',
                coverUrl: vod.vod_pic || PLACEHOLDER_COVER,
                description: (vod.vod_content || vod.vod_blurb || '').replace(/<[^>]+>/g, ''),
                year: extractYear(vod.vod_year),
                rating: safeNumber(vod.vod_score, generateRating()),
                playCount: generatePlayCount(),
                isVip: false,
                director: vod.vod_director || '',
                actors: vod.vod_actor || '',
                region: vod.vod_area || '',
                sourceKey: source.name,
                sourceName: source.name,
            };
        });
    } catch (error) {
        console.error('Failed to fetch from', source.name, ':', error.message);
        return [];
    }
}

app.get('/api/tvbox/recommend', async (req, res) => {
    try {
        const limit = Number(req.query.limit) || 20;

        // 获取源列表（动态加载）
        const sources = await getApiSources();

        // 优先从直接API源获取
        let allFilms = [];
        const apiResults = await Promise.allSettled(
            sources.map(source => fetchFromDirectApi(source))
        );

        apiResults.forEach(result => {
            if (result.status === 'fulfilled' && result.value.length > 0) {
                allFilms = allFilms.concat(result.value);
            }
        });

        console.log('Direct API films count:', allFilms.length);

        // 如果直接API获取失败，降级到TVBox源
        if (allFilms.length === 0) {
            console.log('Falling back to TVBox aggregation');
            const { films } = await aggregateFilms({ page: 1, pageSize: limit, limit });
            allFilms = films;
        }

        // 打乱并限制数量
        const shuffled = shuffle(allFilms).slice(0, limit);

        res.json({
            code: 200,
            data: shuffled,
            message: 'success',
        });
    } catch (error) {
        console.error('Error in /api/tvbox/recommend:', error);
        res.status(500).json({
            code: 500,
            data: null,
            message: error.message,
        });
    }
});

app.get('/api/tvbox/list', async (req, res) => {
    try {
        const page = Number(req.query.page) || 1;
        const pageSize = Number(req.query.pageSize) || 18;
        const { films, total } = await aggregateFilms({ page, pageSize });

        res.json({
            code: 200,
            data: {
                list: films.slice(0, pageSize),
                total: total || films.length,
                page,
                pageSize,
            },
            message: 'success',
        });
    } catch (error) {
        console.error('Error in /api/tvbox/list:', error);
        res.status(500).json({
            code: 500,
            data: null,
            message: error.message,
        });
    }
});

app.get('/api/tvbox/detail/:id', async (req, res) => {
    try {
        const { id } = req.params;
        console.log('Detail request for ID:', id);

        // 首先从缓存获取（直接API源的数据）
        const cached = filmIndex.get(id);
        if (cached) {
            console.log('Found in cache, source:', cached.siteKey);

            // 如果有缓存的vod数据，构建完整的电影信息
            if (cached.vod) {
                const vod = cached.vod;
                const film = {
                    id: id,
                    title: vod.vod_name || 'Unknown',
                    coverUrl: vod.vod_pic || PLACEHOLDER_COVER,
                    description: (vod.vod_content || vod.vod_blurb || '').replace(/<[^>]+>/g, ''),
                    year: extractYear(vod.vod_year),
                    rating: safeNumber(vod.vod_score, generateRating()),
                    playCount: generatePlayCount(),
                    isVip: false,
                    director: vod.vod_director || '未知',
                    actors: vod.vod_actor || '未知',
                    region: vod.vod_area || '未知',
                    sourceKey: cached.siteKey,
                    sourceName: cached.siteKey,
                    language: vod.vod_lang || '国语',
                    duration: 0,
                    // 添加解析后的播放列表
                    episodes: parseEpisodes(vod.vod_play_url, vod.vod_play_from),
                };

                return res.json({
                    code: 200,
                    data: film,
                    message: 'success',
                });
            }

            // 尝试从API重新获取详情
            if (cached.apiUrl && cached.vodId) {
                try {
                    const detailUrl = `${cached.apiUrl}?ac=detail&ids=${cached.vodId}`;
                    console.log('Fetching fresh detail:', detailUrl);
                    const response = await axiosGetWithRetry(detailUrl, {
                        timeout: REQUEST_TIMEOUT_MS + 5000,
                        headers: { 'User-Agent': 'okhttp/4.10.0' },
                    }, { retries: 2 });
                    const list = response.data.list || [];
                    if (list.length > 0) {
                        const vod = list[0];
                        const film = {
                            id: id,
                            title: vod.vod_name || 'Unknown',
                            coverUrl: vod.vod_pic || PLACEHOLDER_COVER,
                            description: (vod.vod_content || vod.vod_blurb || '').replace(/<[^>]+>/g, ''),
                            year: extractYear(vod.vod_year),
                            rating: safeNumber(vod.vod_score, generateRating()),
                            playCount: generatePlayCount(),
                            isVip: false,
                            director: vod.vod_director || '未知',
                            actors: vod.vod_actor || '未知',
                            region: vod.vod_area || '未知',
                            sourceKey: cached.siteKey,
                            sourceName: cached.siteKey,
                        };

                        // 更新缓存
                        cached.vod = vod;
                        filmIndex.set(id, cached);

                        return res.json({
                            code: 200,
                            data: film,
                            message: 'success',
                        });
                    }
                } catch (apiError) {
                    console.error('API fetch failed:', apiError.message);
                }
            }
        }

        // 降级到原有的TVBox解析逻辑
        const parsed = parseFilmId(id);
        let siteKey = parsed?.siteKey;
        let vodId = parsed?.vodId;

        if (!siteKey || !vodId) {
            siteKey = cached?.siteKey;
            vodId = cached?.vodId;
        }

        if (!siteKey || !vodId) {
            return res.status(404).json({
                code: 404,
                data: null,
                message: 'Film not found',
            });
        }

        const sites = await getAllSites();
        let site = sites.find((item) => item.key === siteKey);

        // 如果 TVBox 源中没有，尝试从直接 API 源获取
        if (!site) {
            const apiSources = await getApiSources();
            const apiSource = apiSources.find((s) => s.name === siteKey);
            if (apiSource) {
                console.log('Found in API sources:', siteKey);
                try {
                    const detailUrl = `${apiSource.api}?ac=detail&ids=${vodId}`;
                    console.log('Fetching from API source:', detailUrl);
                    const response = await axiosGetWithRetry(detailUrl, {
                        timeout: REQUEST_TIMEOUT_MS + 5000,
                        headers: { 'User-Agent': 'okhttp/4.10.0' },
                    }, { retries: 2 });
                    const list = response.data.list || [];
                    if (list.length > 0) {
                        const vod = list[0];
                        const film = {
                            id: id,
                            title: vod.vod_name || 'Unknown',
                            coverUrl: vod.vod_pic || PLACEHOLDER_COVER,
                            description: (vod.vod_content || vod.vod_blurb || '').replace(/<[^>]+>/g, ''),
                            year: extractYear(vod.vod_year),
                            rating: safeNumber(vod.vod_score, generateRating()),
                            playCount: generatePlayCount(),
                            isVip: false,
                            director: vod.vod_director || '未知',
                            actors: vod.vod_actor || '未知',
                            region: vod.vod_area || '未知',
                            sourceKey: siteKey,
                            sourceName: siteKey,
                            language: vod.vod_lang || '国语',
                            duration: 0,
                            // 添加解析后的播放列表
                            episodes: parseEpisodes(vod.vod_play_url, vod.vod_play_from),
                        };

                        // 缓存到 filmIndex
                        filmIndex.set(id, { siteKey, vodId, vod, apiUrl: apiSource.api });

                        return res.json({
                            code: 200,
                            data: film,
                            message: 'success',
                        });
                    }
                } catch (apiError) {
                    console.error('API source fetch failed:', apiError.message);
                }
            }
            return res.status(404).json({
                code: 404,
                data: null,
                message: 'Source not found',
            });
        }

        const detail = await fetchSiteDetail(site, vodId);
        if (!detail) {
            return res.status(404).json({
                code: 404,
                data: null,
                message: 'Film not found',
            });
        }

        res.json({
            code: 200,
            data: detail.film,
            message: 'success',
        });
    } catch (error) {
        console.error('Error in /api/tvbox/detail:', error);
        res.status(500).json({
            code: 500,
            data: null,
            message: error.message,
        });
    }
});

app.get('/api/tvbox/play/:id', async (req, res) => {
    try {
        const { id } = req.params;
        console.log('Play request for ID:', id);

        // 首先尝试从缓存获取（直接API源的数据）
        const cached = filmIndex.get(id);
        if (cached && cached.vod) {
            console.log('Found in cache, source:', cached.siteKey);

            // 如果缓存中有vod数据，直接解析播放链接
            const playInfo = parsePlayInfo(cached.vod);

            if (playInfo.playUrl && playInfo.playUrl.startsWith('http')) {
                console.log('Direct m3u8 URL found:', playInfo.playUrl.substring(0, 80));

                // 直接返回原始 URL，不使用任何代理
                console.log('Direct playback (no proxy):', playInfo.playUrl.substring(0, 80));

                return res.json({
                    code: 200,
                    data: playInfo,
                    message: 'success',
                });
            }

            // 如果没有缓存的播放链接，尝试从API重新获取详情
            if (cached.apiUrl) {
                console.log('Fetching fresh detail from API:', cached.apiUrl);
                try {
                    const detailUrl = `${cached.apiUrl}?ac=detail&ids=${cached.vodId}`;
                    const response = await axiosGetWithRetry(detailUrl, {
                        timeout: REQUEST_TIMEOUT_MS + 5000,
                        headers: { 'User-Agent': 'okhttp/4.10.0' },
                    }, { retries: 2 });
                    const list = response.data.list || [];
                    if (list.length > 0) {
                        const freshPlayInfo = parsePlayInfo(list[0]);
                        console.log('Fresh play URL:', freshPlayInfo.playUrl?.substring(0, 80));
                        return res.json({
                            code: 200,
                            data: freshPlayInfo,
                            message: 'success',
                        });
                    }
                } catch (apiError) {
                    console.error('API fetch failed:', apiError.message);
                }
            }
        }

        // 降级到原有的TVBox解析逻辑
        const parsed = parseFilmId(decodeURIComponent(id));
        let siteKey = parsed?.siteKey;
        let vodId = parsed?.vodId;
        console.log('Parsed from ID:', { siteKey, vodId });

        if (!siteKey || !vodId) {
            siteKey = cached?.siteKey;
            vodId = cached?.vodId;
        }

        if (!siteKey || !vodId) {
            console.log('Film not found in index');
            return res.json({
                code: 200,
                data: { playUrl: '', episodes: [] },
                message: 'Film not found',
            });
        }

        const sites = await getAllSites();
        const site = sites.find((item) => item.key === siteKey);
        if (!site) {
            console.log('Source not found:', siteKey);
            return res.json({
                code: 200,
                data: { playUrl: '', episodes: [] },
                message: 'Source not found',
            });
        }

        console.log('Fetching detail from site:', site.name);
        const detail = await fetchSiteDetail(site, vodId);
        if (!detail) {
            return res.json({
                code: 200,
                data: { playUrl: '', episodes: [] },
                message: 'Film not found',
            });
        }

        console.log('Parsing play info...');
        const playInfo = parsePlayInfo(detail.vod || {});
        console.log('Play info:', { playUrl: playInfo.playUrl?.substring(0, 50), episodes: playInfo.episodes?.length });

        res.json({
            code: 200,
            data: playInfo,
            message: 'success',
        });
    } catch (error) {
        console.error('Error in /api/tvbox/play:', error.message);
        res.status(200).json({
            code: 200,
            data: { playUrl: '', episodes: [] },
            message: 'Parse error: ' + error.message,
        });
    }
});

app.get('/api/tvbox/search', async (req, res) => {
    try {
        const keyword = String(req.query.keyword || '').trim();
        if (!keyword) {
            res.json({
                code: 200,
                data: [],
                message: 'success',
            });
            return;
        }

        const { films } = await aggregateFilms({
            page: 1,
            pageSize: 20,
            keyword,
            onlySearchable: true,
        });

        res.json({
            code: 200,
            data: films,
            message: 'success',
        });
    } catch (error) {
        console.error('Error in /api/tvbox/search:', error);
        res.status(500).json({
            code: 500,
            data: null,
            message: error.message,
        });
    }
});

// M3U8代理接口 - 解决CORS和防盗链问题
app.get('/api/tvbox/m3u8', async (req, res) => {
    try {
        const url = req.query.url;
        const refererParam = req.query.referer;
        if (!url) {
            return res.status(400).send('Missing url parameter');
        }

        console.log('Proxying m3u8:', url);

        // 如果 URL 是播放器页面（没有 .m3u8 后缀），自动加上 /index.m3u8
        let finalUrl = url;
        const urlObj = new URL(url);
        const pathname = urlObj.pathname;

        // 检测是否为播放器页面地址（通常是 /play/xxx 格式，没有扩展名）
        if (!pathname.includes('.') && (pathname.includes('/play/') || pathname.includes('/hls/'))) {
            // 尝试添加 /index.m3u8
            finalUrl = url.endsWith('/') ? url + 'index.m3u8' : url + '/index.m3u8';
            console.log('Auto-appended /index.m3u8:', finalUrl);
        }

        const host = urlObj.host;

        // 域名特定的Referer映射（针对有防盗链的CDN）
        const domainRefererMap = {
            // FFZY 系列 CDN - 使用通配模式
            'ffzy': 'https://www.ffzyplay.com/',
            // 其他资源站
            'hongniuzy': 'https://www.hongniuzy.com/',
            'lziapi': 'https://www.lziapi.com/',
            'lz15uu': 'https://www.lziapi.com/',  // 量子资源 CDN
            'guangsuapi': 'https://www.guangsu.com/',
            'xinlangapi': 'https://www.xinlang.com/',
            'wolongzy': 'https://www.wolongzy.tv/',
            'xluuss': 'https://www.guangsu.com/',
            'gsuus': 'https://www.guangsu.com/',
            'svipsvip': 'https://www.ffzyplay.com/',
        };

        // 根据域名获取特定Referer
        function getDomainReferer(hostname) {
            for (const [pattern, referer] of Object.entries(domainRefererMap)) {
                if (hostname.includes(pattern)) {
                    return referer;
                }
            }
            return null;
        }

        const specificReferer = getDomainReferer(host);

        // 多种策略，针对严格防盗链 CDN (如 ffzy)
        // 策略矩阵：UA x Headers x Referer
        const userAgents = [
            // 1. 移动端 UA (通常限制更宽松)
            'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
            // 2. iOS UA
            'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1',
            // 3. Desktop Chrome
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
            // 4. 播放器 UA (模拟 OkHttp)
            'okhttp/4.12.0',
            // 5. 空白 UA
            '',
        ];

        const refererStrategies = [
            // 带参数传入的 referer
            ...(refererParam ? [{ referer: String(refererParam), origin: (() => { try { return new URL(String(refererParam)).origin; } catch { return null; } })() }] : []),
            // 域名特定 referer
            ...(specificReferer ? [{ referer: specificReferer, origin: specificReferer.replace(/\/$/, '') }] : []),
            // 自身 origin
            { referer: urlObj.origin + '/', origin: urlObj.origin },
            // 完全裸请求
            { referer: null, origin: null },
            // 空串
            { referer: '', origin: null },
        ];

        let response = null;
        let successStrategy = null;

        // 双重循环：先试不同 referer，每种 referer 试多个 UA
        outerLoop:
        for (const strategy of refererStrategies) {
            for (const ua of userAgents) {
                try {
                    // 最简化 headers，避免被特征识别
                    const headers = {
                        'User-Agent': ua || 'Mozilla/5.0',
                        'Accept': '*/*',
                        'Accept-Language': 'zh-CN,zh;q=0.9',
                        'Connection': 'keep-alive',
                    };

                    if (strategy.referer) headers['Referer'] = strategy.referer;
                    if (strategy.origin) headers['Origin'] = strategy.origin;

                    // 尝试不带 Sec-Fetch 等复杂 headers
                    response = await axiosGetWithRetry(finalUrl, {
                        timeout: REQUEST_TIMEOUT_MS + 5000,
                        headers,
                        responseType: 'text',
                        maxRedirects: 5,
                        validateStatus: (status) => status >= 200 && status < 400,
                        // 禁用代理以避免额外指纹
                        proxy: false,
                    }, { retries: 1, baseDelayMs: 200 });

                    if (response.status === 200 && response.data) {
                        successStrategy = strategy;
                        console.log(`M3U8 fetched OK: UA="${ua.substring(0, 30)}..." Referer="${strategy.referer || 'none'}"`);
                        break outerLoop;
                    }
                } catch (e) {
                    // 继续尝试下一个组合
                }
            }
            console.log('All UA strategies failed for referer:', strategy.referer);
        }

        if (!response || !response.data) {
            console.error('M3U8 fetch failed after all strategies');
            return res.status(502).send('Failed to fetch m3u8');
        }

        let content = response.data;

        // 验证 M3U8 内容有效性
        if (typeof content === 'string' && !content.includes('#EXTM3U')) {
            // 如果返回的是 HTML，尝试提取其中的 m3u8 链接
            // 常见模式：var main = "xxx.m3u8"; 或 <video src="xxx.m3u8">
            const m3u8Match = content.match(/["']([^"']+\.m3u8[^"']*)["']/) ||
                content.match(/src=["']([^"']+\.m3u8[^"']*)["']/);

            if (m3u8Match && m3u8Match[1]) {
                let realM3u8Url = m3u8Match[1];
                // 处理相对路径
                if (!realM3u8Url.startsWith('http')) {
                    if (realM3u8Url.startsWith('/')) {
                        realM3u8Url = new URL(url).origin + realM3u8Url;
                    } else {
                        realM3u8Url = url.substring(0, url.lastIndexOf('/') + 1) + realM3u8Url;
                    }
                }

                console.log('Extracted real M3U8 URL from HTML:', realM3u8Url);

                // 递归调用自己去获取真正的 m3u8
                // 注意：这里简单的重定向客户端，或者再次发起请求
                // 为了简单起见，我们重定向客户端到代理接口，参数为真正的URL
                const redirectUrl = `/api/tvbox/m3u8?url=${encodeURIComponent(realM3u8Url)}${refererParam ? '&referer=' + encodeURIComponent(refererParam) : ''}`;
                return res.redirect(redirectUrl);
            }

            console.error('Invalid M3U8 content: missing #EXTM3U tag');
            // Log first 100 chars to debug
            console.log('Invalid content start:', content.substring(0, 100).replace(/\n/g, '\\n'));
            return res.status(502).send('Invalid M3U8 content');
        }

        const effectiveReferer = (successStrategy && successStrategy.referer !== undefined) ? successStrategy.referer : (refererParam ? String(refererParam) : null);

        // 如果是m3u8文件，需要将相对路径转换为代理URL
        if (finalUrl.includes('.m3u8') || content.includes('#EXTINF')) {
            const m3u8UrlObj = new URL(finalUrl);
            const baseUrl = finalUrl.substring(0, finalUrl.lastIndexOf('/') + 1);
            const origin = m3u8UrlObj.origin;

            // 辅助函数：解析相对路径为完整URL
            function resolveUrl(path) {
                if (!path) return null;
                // 已经是完整URL
                if (path.startsWith('http://') || path.startsWith('https://')) {
                    if (path.includes('/api/tvbox/enc.key')) {
                        return baseUrl + 'enc.key';
                    }
                    return path;
                }
                // 已经是代理URL，不要再代理（但 enc.key 例外）
                if (path.includes('/api/tvbox/')) {
                    if (path.includes('/api/tvbox/enc.key')) {
                        // 将占位 key 还原为真实 key 路径
                        path = 'enc.key';
                    } else {
                        return null;
                    }
                }
                // 绝对路径 (以/开头)
                if (path.startsWith('/')) {
                    return origin + path;
                }
                // 相对路径
                return baseUrl + path;
            }

            // 处理m3u8中的相对路径
            content = content.split('\n').map(line => {
                line = line.trim();
                if (!line) return line;

                // 处理注释行中的URI
                if (line.startsWith('#')) {
                    if (line.includes('URI=')) {
                        line = line.replace(/URI="([^"]+)"/g, (match, uri) => {
                            const normalizedUri = uri.includes('/api/tvbox/enc.key') ? 'enc.key' : uri;
                            const fullUrl = resolveUrl(normalizedUri);
                            if (!fullUrl) return match;
                            const refererQuery = effectiveReferer ? `&referer=${encodeURIComponent(effectiveReferer)}` : '';
                            return `URI="/api/tvbox/proxy?url=${encodeURIComponent(fullUrl)}${refererQuery}"`;
                        });
                        line = line.replace(/URI=([^",\s]+)/g, (match, uri) => {
                            const normalizedUri = uri.includes('/api/tvbox/enc.key') ? 'enc.key' : uri;
                            const fullUrl = resolveUrl(normalizedUri);
                            if (!fullUrl) return match;
                            const refererQuery = effectiveReferer ? `&referer=${encodeURIComponent(effectiveReferer)}` : '';
                            return `URI=/api/tvbox/proxy?url=${encodeURIComponent(fullUrl)}${refererQuery}`;
                        });
                    }
                    return line;
                }

                // 处理ts片段和子m3u8
                const fullUrl = resolveUrl(line);
                if (!fullUrl) return line; // 如果解析失败，保持原样

                const refererQuery = effectiveReferer ? `&referer=${encodeURIComponent(effectiveReferer)}` : '';
                const isSubM3u8 = fullUrl.includes('.m3u8') || fullUrl.includes('/play/') || fullUrl.includes('/share/');
                const proxyBase = isSubM3u8 ? '/api/tvbox/m3u8' : '/api/tvbox/proxy';
                return `${proxyBase}?url=${encodeURIComponent(fullUrl)}${refererQuery}`;
            }).join('\n');
        }

        res.set({
            'Content-Type': 'application/vnd.apple.mpegurl',
            'Access-Control-Allow-Origin': '*',
        });
        res.send(content);
    } catch (error) {
        console.error('M3U8 proxy error:', error.message);
        res.status(500).send('Proxy error: ' + error.message);
    }
});

// 通用代理接口 - 代理ts片段和其他资源
app.get('/api/tvbox/proxy', async (req, res) => {
    try {
        const url = req.query.url;
        const refererParam = req.query.referer;
        if (!url) {
            return res.status(400).send('Missing url parameter');
        }

        const urlObj = new URL(url);
        const host = urlObj.host;

        // 域名特定的Referer映射
        const domainRefererMap = {
            // FFZY 系列 CDN - 使用通配模式
            'ffzy': 'https://www.ffzyplay.com/',
            'svipsvip': 'https://www.ffzyplay.com/',
            // 其他资源站
            'hongniuzy': 'https://www.hongniuzy.com/',
            'lziapi': 'https://www.lziapi.com/',
            'guangsuapi': 'https://www.guangsu.com/',
            'wolongzy': 'https://www.wolongzy.tv/',
            'xluuss': 'https://www.guangsu.com/',
            'gsuus': 'https://www.guangsu.com/',
        };

        function getDomainReferer(hostname) {
            for (const [pattern, referer] of Object.entries(domainRefererMap)) {
                if (hostname.includes(pattern)) return referer;
            }
            return null;
        }

        const specificReferer = getDomainReferer(host);

        // 多种策略尝试获取资源
        const strategies = [
            ...(refererParam ? [{
                referer: String(refererParam), origin: (() => {
                    try {
                        const o = new URL(String(refererParam));
                        return o.origin;
                    } catch {
                        return null;
                    }
                })()
            }] : []),
            ...(specificReferer ? [{ referer: specificReferer, origin: specificReferer.replace(/\/$/, '') }] : []),
            { referer: urlObj.origin, origin: urlObj.origin },
            { referer: url, origin: urlObj.origin },
            { referer: 'https://jx.xmflv.com/', origin: 'https://jx.xmflv.com' },
            { referer: '', origin: null },
            { referer: null, origin: null },
        ];

        let response = null;

        for (const strategy of strategies) {
            try {
                // 完整的浏览器指纹模拟
                const headers = {
                    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
                    'Accept': '*/*',
                    'Accept-Encoding': 'gzip, deflate, br, zstd',
                    'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
                    'Cache-Control': 'no-cache',
                    'Pragma': 'no-cache',
                    'Sec-CH-UA': '"Google Chrome";v="131", "Chromium";v="131", "Not_A Brand";v="24"',
                    'Sec-CH-UA-Mobile': '?0',
                    'Sec-CH-UA-Platform': '"Windows"',
                    'Sec-Fetch-Dest': 'video',
                    'Sec-Fetch-Mode': 'cors',
                    'Sec-Fetch-Site': 'cross-site',
                    'Connection': 'keep-alive',
                    'DNT': '1',
                    'Range': 'bytes=0-',
                };

                if (strategy.referer) headers['Referer'] = strategy.referer;
                if (strategy.origin) headers['Origin'] = strategy.origin;

                response = await axiosGetWithRetry(url, {
                    timeout: REQUEST_TIMEOUT_MS + 20000,
                    headers,
                    responseType: 'arraybuffer',
                    maxRedirects: 5,
                    validateStatus: (status) => status >= 200 && status < 400,
                }, { retries: 2, baseDelayMs: 500 });

                if (response.status === 200 && response.data) break;
            } catch (e) {
                // 继续尝试下一个策略
            }
        }

        if (!response || !response.data) {
            return res.status(502).send('Resource fetch failed');
        }

        // 根据URL设置正确的Content-Type
        let contentType = 'application/octet-stream';
        if (url.includes('.ts')) {
            contentType = 'video/mp2t';
        } else if (url.includes('.m3u8')) {
            contentType = 'application/vnd.apple.mpegurl';
        } else if (url.includes('.key')) {
            contentType = 'application/octet-stream';
        } else if (url.includes('.mp4')) {
            contentType = 'video/mp4';
        }

        res.set({
            'Content-Type': contentType,
            'Access-Control-Allow-Origin': '*',
            'Cache-Control': 'public, max-age=3600',
        });
        res.send(Buffer.from(response.data));
    } catch (error) {
        console.error('Proxy error:', error.message);
        res.status(500).send('Proxy error');
    }
});

app.listen(PORT, async () => {
    console.log(`TVBox Proxy Server running on http://localhost:${PORT}`);
    console.log('CORS enabled for all origins');

    // 启动时测试加载源
    try {
        const sources = await getApiSources();
        console.log(`\n=== Active API Sources (${sources.length}) ===`);
        sources.forEach(s => console.log(`  ✓ ${s.name}: ${s.api.substring(0, 50)}...`));
    } catch (e) {
        console.error('Failed to load sources on startup:', e.message);
    }
});

// 调试端点：查看当前使用的源
app.get('/api/tvbox/debug/sources', async (req, res) => {
    try {
        const sources = await getApiSources();
        res.json({
            code: 200,
            count: sources.length,
            sources: sources.map(s => ({
                name: s.name,
                api: s.api,
                type: s.type,
                priority: s.priority
            })),
            message: 'success'
        });
    } catch (e) {
        res.status(500).json({ code: 500, message: e.message });
    }
});

module.exports = app;

