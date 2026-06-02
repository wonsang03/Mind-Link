/**
 * 관리자 서버 로그 뷰어 — templates/admin/logs.html
 * URL 은 #log-meta 의 data-* 속성(Thymeleaf)에서 읽는다.
 */
(function () {
    'use strict';

    const LINE_SEP = '\n';
    const NL_SPLIT = /\r?\n/;

    const meta = document.getElementById('log-meta');
    if (!meta) return;

    const FILES_URL = meta.dataset.filesUrl || '/admin/logs/files';
    const RECENT_URL = meta.dataset.recentUrl || '/admin/logs/recent';
    const STREAM_URL = meta.dataset.streamUrl || '/admin/logs/stream';

    const view = document.getElementById('log-view');
    const status = document.getElementById('log-status');
    const fileSel = document.getElementById('log-file');
    const linesSel = document.getElementById('log-lines');
    const refreshBtn = document.getElementById('log-refresh');
    const preset = document.getElementById('log-preset');
    const filter = document.getElementById('log-filter');
    const levelFilter = document.getElementById('log-level-filter');
    const autoscroll = document.getElementById('log-autoscroll');
    const pause = document.getElementById('log-pause');
    const wrap = document.getElementById('log-wrap');
    const colorize = document.getElementById('log-colorize');
    const linenums = document.getElementById('log-linenums');
    const clearBtn = document.getElementById('log-clear');
    const downloadBtn = document.getElementById('log-download');
    const mergeAccess = document.getElementById('log-merge-access');

    const MAX_LINES = 5000;
    const LEVEL_ORDER = { TRACE: 0, DEBUG: 1, INFO: 2, WARN: 3, ERROR: 4 };
    const PRIMARY_RE = /^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+([A-Z]+)\s+\[([^\]]*)\]\s+(\S+)\s+-\s+([\s\S]*)$/;
    const ACCESS_MSG_RE = /^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\s+(\S+?)(\?[^\s]+)?\s+->\s+(\d+)\s+\((\d+)\s+ms\)/;
    const ACCESS_GROUP_MS = 2500;
    const ACCESS_GROUP_MIN = 2;

    let rawLines = [];
    let buffered = [];
    let currentFileName = null;
    let selectedFile = null;
    let renderTimer = null;
    let evt = null;

    function setStatus(text, cls) {
        status.textContent = text;
        status.className = 'log-status ' + cls;
    }

    function setMeta(text) {
        meta.textContent = text;
    }

    function parsePrimary(line) {
        const m = PRIMARY_RE.exec(line);
        if (!m) return null;
        return { ts: m[1], level: m[2], thread: m[3], logger: m[4], msg: m[5] };
    }

    function buildBlocks(arr) {
        const blocks = [];
        let cur = null;
        for (const line of arr) {
            const p = parsePrimary(line);
            if (p) {
                cur = { primary: line, parsed: p, level: p.level, cont: [] };
                blocks.push(cur);
            } else if (cur) {
                cur.cont.push(line);
            } else {
                cur = { primary: line, parsed: null, level: '', cont: [] };
                blocks.push(cur);
            }
        }
        return blocks;
    }

    function blockText(b) {
        return b.cont.length ? b.primary + LINE_SEP + b.cont.join(LINE_SEP) : b.primary;
    }

    function parseAccessMsg(msg) {
        const m = ACCESS_MSG_RE.exec(msg);
        if (!m) return null;
        return { method: m[1], path: m[2], status: parseInt(m[4], 10), ms: parseInt(m[5], 10) };
    }

    function accessGroupKey(parsed) {
        const a = parseAccessMsg(parsed.msg);
        return a ? a.method + ' ' + a.path : null;
    }

    function parseLogTs(ts) {
        const d = new Date(ts.replace(' ', 'T'));
        return isNaN(d.getTime()) ? 0 : d.getTime();
    }

    /** 연속 ACCESS + 동일 path(쿼리 제외) + 2.5초 이내 → 한 그룹 */
    function mergeAccessGroups(blocks) {
        if (mergeAccess && !mergeAccess.checked) return blocks;
        const out = [];
        let i = 0;
        while (i < blocks.length) {
            const b = blocks[i];
            if (!b.parsed || b.parsed.logger !== 'ACCESS' || b.kind === 'access-group') {
                out.push(b);
                i++;
                continue;
            }
            const key = accessGroupKey(b.parsed);
            if (!key) {
                out.push(b);
                i++;
                continue;
            }
            const items = [b];
            let lastTs = parseLogTs(b.parsed.ts);
            let j = i + 1;
            while (j < blocks.length) {
                const b2 = blocks[j];
                if (!b2.parsed || b2.parsed.logger !== 'ACCESS') break;
                const key2 = accessGroupKey(b2.parsed);
                const ts2 = parseLogTs(b2.parsed.ts);
                if (key2 !== key || (ts2 && lastTs && ts2 - lastTs > ACCESS_GROUP_MS)) break;
                items.push(b2);
                if (ts2) lastTs = ts2;
                j++;
            }
            if (items.length >= ACCESS_GROUP_MIN) {
                out.push({ kind: 'access-group', key: key, items: items, level: 'INFO', parsed: items[0].parsed });
                i = j;
            } else {
                out.push(b);
                i++;
            }
        }
        return out;
    }

    function passes(b) {
        if (b.kind === 'access-group') {
            const q = filter.value.trim().toLowerCase();
            if (q) {
                const hay = b.items.map(function (it) { return blockText(it); }).join(LINE_SEP).toLowerCase();
                if (hay.indexOf(q) < 0) return false;
            }
            if (preset.value === 'APP') return false;
            if (levelFilter.value && LEVEL_ORDER.INFO < LEVEL_ORDER[levelFilter.value]) return false;
            return true;
        }
        const q = filter.value.trim().toLowerCase();
        if (q && blockText(b).toLowerCase().indexOf(q) < 0) return false;
        const min = levelFilter.value;
        if (min) {
            if (!b.level) return false;
            if (LEVEL_ORDER[b.level] < LEVEL_ORDER[min]) return false;
        }
        const p = preset.value;
        const isAccess = !!(b.parsed && b.parsed.logger === 'ACCESS');
        if (p === 'APP' && isAccess) return false;
        if (p === 'ACCESS' && !isAccess) return false;
        return true;
    }

    function span(cls, text) {
        const s = document.createElement('span');
        s.className = cls;
        s.textContent = text;
        return s;
    }

    function badge(level) {
        return span('log-badge log-badge-' + level.toLowerCase(), level);
    }

    function renderBlock(b, lineNo) {
        if (!colorize.checked) {
            const div = document.createElement('div');
            div.className = 'log-line plain';
            div.appendChild(span('log-ln', lineNo != null ? String(lineNo) : ''));
            div.appendChild(span('log-msg', blockText(b)));
            return div;
        }
        const block = document.createElement('div');
        block.className = 'log-block' + (b.level ? ' lvl-' + b.level.toLowerCase() : '');

        const line = document.createElement('div');
        line.className = 'log-line';
        line.appendChild(span('log-ln', lineNo != null ? String(lineNo) : ''));
        if (b.parsed) {
            line.appendChild(span('log-ts', b.parsed.ts));
            line.appendChild(badge(b.parsed.level));
            line.appendChild(span('log-logger', b.parsed.logger));
            line.appendChild(span('log-msg', b.parsed.msg));
        } else {
            line.appendChild(span('log-msg', b.primary));
        }

        if (b.cont.length) {
            const body = document.createElement('div');
            body.className = 'log-trace-body collapsed';
            body.textContent = b.cont.join(LINE_SEP);
            const toggle = document.createElement('button');
            toggle.type = 'button';
            toggle.className = 'log-trace-toggle';
            const label = function (collapsed) {
                return (collapsed ? '▸ 스택트레이스 ' : '▾ 스택트레이스 ') + b.cont.length + '줄';
            };
            toggle.textContent = label(true);
            toggle.addEventListener('click', function () {
                const collapsed = body.classList.toggle('collapsed');
                toggle.textContent = label(collapsed);
            });
            line.appendChild(toggle);
            block.appendChild(line);
            block.appendChild(body);
        } else {
            block.appendChild(line);
        }
        return block;
    }

    function renderAccessGroup(g, lineNo) {
        const items = g.items;
        const parsed = items[0].parsed;
        const accesses = items.map(function (it) { return parseAccessMsg(it.parsed.msg); }).filter(Boolean);
        const statuses = accesses.map(function (a) { return a.status; });
        const allOk = statuses.every(function (s) { return s === statuses[0]; });
        const msList = accesses.map(function (a) { return a.ms; });
        const msMin = Math.min.apply(null, msList);
        const msMax = Math.max.apply(null, msList);
        const msLabel = msMin === msMax ? msMin + ' ms' : msMin + '–' + msMax + ' ms';
        const summary = g.key + ' → ' + (allOk ? statuses[0] : 'mixed') + ' (' + msLabel + ') · ' + items.length + '건';

        const wrap = document.createElement('div');
        wrap.className = 'log-block log-access-group lvl-info';

        const head = document.createElement('div');
        head.className = 'log-line log-access-head';
        head.appendChild(span('log-ln', lineNo != null ? String(lineNo) : ''));
        if (colorize.checked && parsed) {
            head.appendChild(span('log-ts', parsed.ts));
            head.appendChild(badge('INFO'));
            head.appendChild(span('log-logger', 'ACCESS'));
        }
        const toggle = document.createElement('button');
        toggle.type = 'button';
        toggle.className = 'log-trace-toggle log-access-toggle';
        const body = document.createElement('div');
        body.className = 'log-access-body collapsed';
        items.forEach(function (it) {
            const row = document.createElement('div');
            row.className = 'log-access-detail';
            row.textContent = it.parsed ? it.parsed.msg : it.primary;
            body.appendChild(row);
        });
        const label = function (collapsed) {
            return (collapsed ? '▸ ' : '▾ ') + summary;
        };
        toggle.textContent = label(true);
        toggle.addEventListener('click', function () {
            const collapsed = body.classList.toggle('collapsed');
            toggle.textContent = label(collapsed);
        });
        head.appendChild(toggle);
        wrap.appendChild(head);
        wrap.appendChild(body);
        return wrap;
    }

    function render() {
        const blocks = mergeAccessGroups(buildBlocks(rawLines));
        const frag = document.createDocumentFragment();
        let no = 0;
        for (const b of blocks) {
            no++;
            if (!passes(b)) continue;
            if (b.kind === 'access-group') {
                frag.appendChild(renderAccessGroup(b, no));
            } else {
                frag.appendChild(renderBlock(b, no));
            }
        }
        view.innerHTML = '';
        view.appendChild(frag);
        if (autoscroll.checked) view.scrollTop = view.scrollHeight;
    }

    function scheduleRender() {
        if (renderTimer) return;
        renderTimer = setTimeout(function () {
            renderTimer = null;
            render();
        }, 200);
    }

    function trimRaw() {
        if (rawLines.length > MAX_LINES) rawLines.splice(0, rawLines.length - MAX_LINES);
    }

    function handleChunk(chunk) {
        const parts = chunk.split(NL_SPLIT);
        if (parts.length && parts[parts.length - 1] === '') parts.pop();
        for (const ln of parts) {
            if (pause.checked) buffered.push(ln);
            else rawLines.push(ln);
        }
        if (pause.checked) {
            if (buffered.length > MAX_LINES) buffered.splice(0, buffered.length - MAX_LINES);
        } else {
            trimRaw();
            scheduleRender();
        }
    }

    function fetchJson(url, ms) {
        const ctrl = new AbortController();
        const timer = setTimeout(function () { ctrl.abort(); }, ms || 60000);
        return fetch(url, { signal: ctrl.signal, credentials: 'same-origin' })
            .finally(function () { clearTimeout(timer); })
            .then(function (r) {
                if (!r.ok) {
                    return r.text().then(function (t) {
                        throw new Error('HTTP ' + r.status + (t ? ': ' + t.slice(0, 120) : ''));
                    });
                }
                return r.json();
            });
    }

    function loadFiles(thenSelect) {
        setMeta('파일 목록 불러오는 중…');
        setStatus('목록 조회 중…', 'log-status-connecting');
        fetchJson(FILES_URL, 15000)
            .then(function (data) {
                if (data.error) {
                    setMeta(data.error);
                    setStatus('목록 실패', 'log-status-error');
                    return;
                }
                currentFileName = data.current;
                fileSel.innerHTML = '';
                (data.files || []).forEach(function (f) {
                    const opt = document.createElement('option');
                    opt.value = f.name;
                    opt.textContent = f.name + '  (' + f.sizeReadable + ')' + (f.isCurrent ? '  · 현재' : '');
                    fileSel.appendChild(opt);
                });
                if ((data.files || []).length === 0) {
                    setMeta('logs/ 디렉터리에 로그 파일이 없습니다. 서버를 실행하면 mindlink.log 가 생성됩니다.');
                    setStatus('파일 없음', 'log-status-connecting');
                    return;
                }
                const target = thenSelect || (data.files.find(function (f) { return f.isCurrent; }) || data.files[0]).name;
                fileSel.value = target;
                loadFile(target);
            })
            .catch(function (err) {
                const msg = err.name === 'AbortError'
                    ? '파일 목록 요청 시간이 초과되었습니다.'
                    : '파일 목록을 불러오지 못했습니다. (관리자 로그인·서버 실행 여부 확인)';
                setMeta(msg);
                setStatus('목록 실패', 'log-status-error');
                console.error('loadFiles', err);
            });
    }

    function loadFile(name) {
        if (!name) return;
        selectedFile = name;
        setMeta('로그 불러오는 중… (' + name + ')');
        const url = RECENT_URL + '?file=' + encodeURIComponent(name) + '&lines=' + linesSel.value;
        fetchJson(url, 120000)
            .then(function (data) {
                if (data.error) {
                    setMeta(data.error);
                    setStatus('로드 실패', 'log-status-error');
                    return;
                }
                rawLines = (data.lines || []).slice();
                trimRaw();
                setMeta(data.exists
                    ? ('파일: ' + data.path + ' · ' + (data.size || 0).toLocaleString() + ' B · ' + rawLines.length + '줄')
                    : ('파일 없음 (' + data.path + ') — 새 로그가 기록되면 표시됩니다.'));
                try {
                    render();
                } catch (e) {
                    console.error('render', e);
                    setMeta('로그 표시 중 오류가 났습니다. 「색상·열 분리」를 끄고 다시 시도해 보세요.');
                }
                updateLive();
            })
            .catch(function (err) {
                const msg = err.name === 'AbortError'
                    ? '로그 파일이 커서 읽기 시간이 초과되었습니다. 줄 수를 줄이거나 .gz 대신 현재 로그를 선택하세요.'
                    : '로그를 불러오지 못했습니다.';
                setMeta(msg);
                setStatus('로드 실패', 'log-status-error');
                console.error('loadFile', err);
            });
    }

    function updateLive() {
        if (selectedFile && selectedFile === currentFileName) {
            connect();
        } else {
            disconnect();
            setStatus('과거 로그 · 실시간 아님', 'log-status-connecting');
        }
    }

    function connect() {
        disconnect();
        setStatus('연결 중…', 'log-status-connecting');
        evt = new EventSource(STREAM_URL);
        evt.addEventListener('ready', function () { setStatus('● 실시간 연결됨', 'log-status-live'); });
        evt.addEventListener('log', function (e) { handleChunk(e.data); });
        evt.addEventListener('ping', function () {});
        evt.onerror = function () {
            setStatus('연결 끊김 — 재시도 중…', 'log-status-error');
            disconnect();
            if (selectedFile === currentFileName) setTimeout(connect, 3000);
        };
    }

    function disconnect() {
        if (evt) {
            try { evt.close(); } catch (e) { /* ignore */ }
            evt = null;
        }
    }

    fileSel.addEventListener('change', function () { loadFile(fileSel.value); });
    linesSel.addEventListener('change', function () { loadFile(selectedFile); });
    refreshBtn.addEventListener('click', function () { loadFiles(selectedFile); });
    preset.addEventListener('change', render);
    filter.addEventListener('input', render);
    levelFilter.addEventListener('change', render);
    colorize.addEventListener('change', render);
    if (mergeAccess) mergeAccess.addEventListener('change', render);
    wrap.addEventListener('change', function () { view.classList.toggle('log-nowrap', !wrap.checked); });
    linenums.addEventListener('change', function () { view.classList.toggle('show-ln', linenums.checked); });
    pause.addEventListener('change', function () {
        if (!pause.checked && buffered.length) {
            rawLines = rawLines.concat(buffered);
            buffered = [];
            trimRaw();
            render();
        }
    });
    clearBtn.addEventListener('click', function () { rawLines = []; view.innerHTML = ''; });
    downloadBtn.addEventListener('click', function () {
        const blob = new Blob([rawLines.join(LINE_SEP)], { type: 'text/plain;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = (selectedFile || 'mindlink') + '-' + new Date().toISOString().replace(/[:.]/g, '-') + '.log';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    });

    loadFiles();
})();
