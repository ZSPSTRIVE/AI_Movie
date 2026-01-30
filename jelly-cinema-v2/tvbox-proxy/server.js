const express = require('express');
const axios = require('axios');
const cors = require('cors');
const { XMLParser } = require('fast-xml-parser');

const app = express();
const PORT = process.env.PORT || 3001;

app.use(cors());
app.use(express.json());

const TV_SOURCES = [
    // 直接提供m3u8链接的采集API源
    'https://cj.lziapi.com/api.php/provide/vod/from/lzm3u8/at/json', // 量子资源
    'https://api.1080zyku.com/inc/apijson.php', // 1080资源
    'https://360zy.com/api.php/provide/vod/at/json', // 360资源
    // 备用TVBox配置源
    'https://6800.kstore.vip/fish.json',
    'http://cdn.qiaoji8.com/tvbox.json',
];

// 直接采集API列表（提供m3u8直链）
const DIRECT_API_SOURCES = [
    { name: '量子资源', api: 'https://cj.lziapi.com/api.php/provide/vod/', type: 'json' },
    { name: '1080资源', api: 'https://api.1080zyku.com/inc/apijson.php', type: 'json' },
    { name: '非凡资源', api: 'https://cj.ffzyapi.com/api.php/provide/vod/', type: 'json' },
    { name: '光速资源', api: 'https://api.guangsuapi.com/api.php/provide/vod/', type: 'json' },
    { name: '新浪资源', api: 'https://api.xinlangapi.com/xinlangapi.php/provide/vod/', type: 'json' },
];

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

async function fetchRemote(url) {
    try {
        const response = await axios.get(url, {
            timeout: REQUEST_TIMEOUT_MS,
            responseType: 'arraybuffer',
            headers: {
                'User-Agent': 'okhttp/4.10.0',
                'Accept': 'application/json,text/plain,*/*',
            },
            validateStatus: status => status >= 200 && status < 400,
        });
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

    const results = await Promise.allSettled(
        TV_SOURCES.map(async (sourceUrl) => {
            const data = await fetchTVBoxSource(sourceUrl);
            if (!data || !Array.isArray(data.sites)) return [];
            return data.sites.map((site, index) => normalizeSite(site, sourceUrl, index));
        })
    );

    const sites = [];
    const seen = new Set();
    for (const result of results) {
        if (result.status !== 'fulfilled') continue;
        for (const site of result.value) {
            const dedupeKey = `${site.key}|${site.api}|${site.type}`;
            if (seen.has(dedupeKey)) continue;
            seen.add(dedupeKey);
            sites.push(site);
        }
    }

    setCache(cacheKey, sites, CACHE_TTL_MS);
    return sites;
}

function buildApiCandidates(site) {
    const candidates = [];
    if (isHttpUrl(site.api)) {
        candidates.push(normalizeUrl(site.api));
    }
    if (typeof site.ext === 'string' && isHttpUrl(site.ext)) {
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

    // 优先选择m3u8直链
    let bestUrl = '';
    for (const ep of episodes) {
        if (ep.url && (ep.url.includes('.m3u8') || ep.url.includes('index.m3u8'))) {
            bestUrl = ep.url;
            break;
        }
    }
    if (!bestUrl && episodes.length > 0) {
        bestUrl = episodes[0].url;
    }

    return { playUrl: bestUrl, episodes };
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
        const response = await axios.get(url, {
            timeout: 15000,
            headers: { 'User-Agent': 'okhttp/4.10.0' },
        });
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

        // 优先从直接API源获取
        let allFilms = [];
        const apiResults = await Promise.allSettled(
            DIRECT_API_SOURCES.map(source => fetchFromDirectApi(source))
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
                    const response = await axios.get(detailUrl, {
                        timeout: 15000,
                        headers: { 'User-Agent': 'okhttp/4.10.0' },
                    });
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
        const site = sites.find((item) => item.key === siteKey);
        if (!site) {
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
                    const response = await axios.get(detailUrl, {
                        timeout: 15000,
                        headers: { 'User-Agent': 'okhttp/4.10.0' },
                    });
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

// M3U8代理接口 - 解决CORS问题
app.get('/api/tvbox/m3u8', async (req, res) => {
    try {
        const url = req.query.url;
        if (!url) {
            return res.status(400).send('Missing url parameter');
        }

        console.log('Proxying m3u8:', url);

        const response = await axios.get(url, {
            timeout: 15000,
            headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
                'Referer': new URL(url).origin,
            },
            responseType: 'text',
        });

        let content = response.data;

        // 如果是m3u8文件，需要将相对路径转换为代理URL
        if (url.includes('.m3u8')) {
            const baseUrl = url.substring(0, url.lastIndexOf('/') + 1);

            // 处理m3u8中的相对路径
            content = content.split('\n').map(line => {
                line = line.trim();
                if (!line || line.startsWith('#')) {
                    // 处理#EXT-X-KEY等包含URI的行
                    if (line.includes('URI="')) {
                        line = line.replace(/URI="([^"]+)"/g, (match, uri) => {
                            if (uri.startsWith('http')) return match;
                            const fullUrl = baseUrl + uri;
                            return `URI="/api/tvbox/proxy?url=${encodeURIComponent(fullUrl)}"`;
                        });
                    }
                    return line;
                }
                // 处理ts片段和子m3u8
                if (!line.startsWith('http')) {
                    const fullUrl = baseUrl + line;
                    return `/api/tvbox/proxy?url=${encodeURIComponent(fullUrl)}`;
                }
                return `/api/tvbox/proxy?url=${encodeURIComponent(line)}`;
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
        if (!url) {
            return res.status(400).send('Missing url parameter');
        }

        const response = await axios.get(url, {
            timeout: 30000,
            headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
                'Referer': new URL(url).origin,
            },
            responseType: 'arraybuffer',
        });

        // 根据URL设置正确的Content-Type
        let contentType = 'application/octet-stream';
        if (url.includes('.ts')) {
            contentType = 'video/mp2t';
        } else if (url.includes('.m3u8')) {
            contentType = 'application/vnd.apple.mpegurl';
        } else if (url.includes('.key')) {
            contentType = 'application/octet-stream';
        }

        res.set({
            'Content-Type': contentType,
            'Access-Control-Allow-Origin': '*',
        });
        res.send(Buffer.from(response.data));
    } catch (error) {
        console.error('Proxy error:', error.message);
        res.status(500).send('Proxy error');
    }
});

app.listen(PORT, () => {
    console.log(`TVBox Proxy Server running on http://localhost:${PORT}`);
    console.log('CORS enabled for all origins');
});

module.exports = app;

