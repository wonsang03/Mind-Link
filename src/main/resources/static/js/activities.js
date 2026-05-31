/* =============================================================================
   activities.js — 추천 활동 렌더러 + 수행 기록 저장
   각 활동은 /activities/{key} 전용 페이지에서 MindLinkActivities.run(key, host) 로 실행.
   완료 시 POST /api/activities 로 기록(비로그인은 서버가 조용히 무시).
   ============================================================================= */
window.MindLinkActivities = (function () {
    'use strict';

    function esc(s) {
        return String(s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    var onLogged = null;
    var askBeforeSave = true;   // 완료 시 '일지에 저장할까요?' 확인 창 표시 여부

    /** 실제 저장(fire-and-forget) + 완료 콜백 */
    function persist(key, payload, moodScore) {
        try {
            fetch('/api/activities', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    activityKey: key,
                    payload: payload || null,
                    moodScore: (moodScore != null ? moodScore : null)
                })
            }).catch(function () {});
        } catch (e) { /* noop */ }
        if (typeof onLogged === 'function') {
            try { onLogged(key); } catch (e) { /* noop */ }
        }
    }

    /** '일지에 저장할까요?' 확인 창. 저장 선택 시 onConfirm 실행. */
    function showSaveDialog(onConfirm) {
        if (document.getElementById('mlSaveDialog')) return;   // 중복 방지
        var ov = document.createElement('div');
        ov.id = 'mlSaveDialog';
        ov.className = 'ml-save-overlay';
        ov.innerHTML =
            '<div class="ml-save-box" role="dialog" aria-modal="true" aria-labelledby="mlSaveTitle">' +
            '<p id="mlSaveTitle" class="ml-save-title">이 활동을 활동 일지에 저장할까요?</p>' +
            '<p class="ml-save-desc">저장하면 나중에 활동 일지에서 다시 확인할 수 있어요.</p>' +
            '<div class="ml-save-actions">' +
            '<button class="btn btn-outline btn-sm" id="mlSaveNo">저장 안 함</button>' +
            '<button class="btn btn-primary btn-sm" id="mlSaveYes">저장하기</button>' +
            '</div></div>';
        document.body.appendChild(ov);
        requestAnimationFrame(function () { ov.classList.add('is-open'); });

        function close() {
            ov.classList.remove('is-open');
            document.removeEventListener('keydown', onKey);
            setTimeout(function () { if (ov.parentNode) ov.parentNode.removeChild(ov); }, 200);
        }
        function onKey(e) { if (e.key === 'Escape') close(); }

        document.getElementById('mlSaveYes').addEventListener('click', function () { close(); onConfirm(); });
        document.getElementById('mlSaveNo').addEventListener('click', close);
        ov.addEventListener('click', function (e) { if (e.target === ov) close(); });
        document.addEventListener('keydown', onKey);
        setTimeout(function () { var y = document.getElementById('mlSaveYes'); if (y) y.focus(); }, 50);
    }

    /** 활동 1회 완료. 기본적으로 저장 여부를 한 번 묻는다. */
    function log(key, payload, moodScore) {
        if (askBeforeSave) {
            showSaveDialog(function () { persist(key, payload, moodScore); });
        } else {
            persist(key, payload, moodScore);
        }
    }

    var ACTS = {

        /* 1. 호흡 운동 */
        breathing: {
            title: '호흡 운동',
            meta : '약 3분 · 신체 활동',
            render: function (el) {
                var PHASES = [
                    { name: '들숨', dur: 4, cls: 'inhale', txt: '천천히 코로 숨을 들이마세요' },
                    { name: '날숨', dur: 6, cls: 'exhale', txt: '천천히 입으로 숨을 내쉬세요' }
                ];
                var CYCLE = 10, TOTAL = CYCLE * 18;   // 약 3분 (한 호흡 10초 × 18회)
                var ring, phEl, numEl, guide, remainEl, startB, resetB;
                var pi = 0, sLeft = 0, timer = null, running = false, elapsed = 0;

                function fmt(s) { var m = Math.floor(s / 60), x = s % 60; return m + ':' + (x < 10 ? '0' : '') + x; }
                function updateRemain() {
                    if (remainEl) remainEl.textContent = '남은 시간 ' + fmt(Math.max(0, TOTAL - elapsed));
                }
                function startPhase(idx) {
                    pi = idx; sLeft = PHASES[idx].dur;
                    ring.className    = 'breath-ring ' + PHASES[idx].cls;
                    phEl.textContent  = PHASES[idx].name;
                    guide.textContent = PHASES[idx].txt;
                    numEl.textContent = sLeft;
                }
                function tick() {
                    elapsed++; updateRemain();
                    sLeft--;
                    if (sLeft <= 0) {
                        var next = (pi + 1) % PHASES.length;
                        if (next === 0 && elapsed >= TOTAL) { finish(); return; }  // 날숨 끝 + 시간 종료 → 완료
                        startPhase(next);
                    } else {
                        numEl.textContent = sLeft;
                    }
                }
                function doStart() {
                    if (running) return;
                    running = true; startB.disabled = true; startB.textContent = '진행 중…';
                    elapsed = 0; updateRemain();
                    startPhase(0); timer = setInterval(tick, 1000);
                }
                function doReset() {
                    clearInterval(timer); mount();   // 멈추고 처음 상태로 (완료 아님 → 기록하지 않음)
                }
                function finish() {
                    clearInterval(timer); running = false;
                    el.innerHTML =
                        '<div class="pmr-done">' +
                        '<p style="font-size:2rem;">🌬️</p>' +
                        '<p style="line-height:1.75;margin:0.5rem 0;">호흡 운동을 마쳤어요.<br>한결 편안해진 호흡을 잠시 느껴보세요.</p>' +
                        '<button id="bAgain" class="btn btn-outline btn-block" style="margin-top:1rem;">다시 하기</button></div>';
                    document.getElementById('bAgain').addEventListener('click', mount);
                    log('breathing', null);
                }
                function mount() {
                    running = false; timer = null; pi = 0; sLeft = 0; elapsed = 0;
                    el.innerHTML =
                        '<div class="act-wrap">' +
                        '<div class="breath-scene"><div class="breath-stage">' +
                        '<div class="breath-ring" id="bRing">' +
                        '<p id="bPhase" class="breath-phase">준비</p>' +
                        '<p id="bNum"   class="breath-num">-</p>' +
                        '</div></div></div>' +
                        '<p id="bGuide" class="act-guide">시작 버튼을 눌러 호흡을 시작하세요</p>' +
                        '<p id="bRemain" class="breath-remain muted">약 ' + Math.round(TOTAL / 60) + '분 동안 진행돼요</p>' +
                        '<div class="act-btns">' +
                        '<button id="bStart" class="btn btn-primary">시작</button>' +
                        '<button id="bReset" class="btn btn-outline">처음으로</button>' +
                        '</div></div>';
                    ring = document.getElementById('bRing');
                    phEl = document.getElementById('bPhase');
                    numEl = document.getElementById('bNum');
                    guide = document.getElementById('bGuide');
                    remainEl = document.getElementById('bRemain');
                    startB = document.getElementById('bStart');
                    resetB = document.getElementById('bReset');
                    startB.addEventListener('click', doStart);
                    resetB.addEventListener('click', doReset);
                }
                mount();
                return function () { clearInterval(timer); };
            }
        },

        /* 2. 느린 호흡 */
        slow_breathing: {
            title: '느린 호흡',
            meta : '약 3분 · 호흡',
            render: function (el) {
                var PHASES = [
                    { name: '들숨', dur: 4, cls: 'inhale', txt: '코로 천천히 들이마셔요' },
                    { name: '날숨', dur: 7, cls: 'exhale', txt: '입으로 더 천천히 내쉬어요' }
                ];
                var CYCLE = 11, TOTAL = CYCLE * 16;   // 약 3분 (한 호흡 11초 × 16회)
                var ring, phEl, numEl, guide, remainEl, startB, resetB;
                var pi = 0, sLeft = 0, timer = null, running = false, elapsed = 0;

                function fmt(s) { var m = Math.floor(s / 60), x = s % 60; return m + ':' + (x < 10 ? '0' : '') + x; }
                function updateRemain() {
                    if (remainEl) remainEl.textContent = '남은 시간 ' + fmt(Math.max(0, TOTAL - elapsed));
                }
                function startPhase(idx) {
                    pi = idx; sLeft = PHASES[idx].dur;
                    ring.className    = 'breath-ring ' + PHASES[idx].cls;
                    phEl.textContent  = PHASES[idx].name;
                    guide.textContent = PHASES[idx].txt;
                    numEl.textContent = sLeft;
                }
                function tick() {
                    elapsed++; updateRemain();
                    sLeft--;
                    if (sLeft <= 0) {
                        var next = (pi + 1) % PHASES.length;
                        if (next === 0 && elapsed >= TOTAL) { finish(); return; }  // 날숨 끝 + 시간 종료 → 완료
                        startPhase(next);
                    } else {
                        numEl.textContent = sLeft;
                    }
                }
                function doStart() {
                    if (running) return;
                    running = true; startB.disabled = true; startB.textContent = '진행 중…';
                    elapsed = 0; updateRemain();
                    startPhase(0); timer = setInterval(tick, 1000);
                }
                function doReset() {
                    clearInterval(timer); mount();   // 멈추고 처음 상태로 (완료 아님 → 기록하지 않음)
                }
                function finish() {
                    clearInterval(timer); running = false;
                    el.innerHTML =
                        '<div class="pmr-done">' +
                        '<p style="font-size:2rem;">🌙</p>' +
                        '<p style="line-height:1.75;margin:0.5rem 0;">느린 호흡을 마쳤어요.<br>차분해진 마음을 잠시 머금어 보세요.</p>' +
                        '<button id="sbAgain" class="btn btn-outline btn-block" style="margin-top:1rem;">다시 하기</button></div>';
                    document.getElementById('sbAgain').addEventListener('click', mount);
                    log('slow_breathing', null);
                }
                function mount() {
                    running = false; timer = null; pi = 0; sLeft = 0; elapsed = 0;
                    el.innerHTML =
                        '<div class="act-wrap">' +
                        '<div class="breath-scene"><div class="breath-stage">' +
                        '<div class="breath-ring" id="sbRing">' +
                        '<p id="sbPhase" class="breath-phase">준비</p>' +
                        '<p id="sbNum"   class="breath-num">-</p>' +
                        '</div></div></div>' +
                        '<p id="sbGuide" class="act-guide">날숨을 들숨보다 길게 — 천천히 내쉬는 데 집중해요</p>' +
                        '<p id="sbRemain" class="breath-remain muted">약 ' + Math.round(TOTAL / 60) + '분 동안 진행돼요</p>' +
                        '<div class="act-btns">' +
                        '<button id="sbStart" class="btn btn-primary">시작</button>' +
                        '<button id="sbReset" class="btn btn-outline">처음으로</button>' +
                        '</div></div>';
                    ring = document.getElementById('sbRing');
                    phEl = document.getElementById('sbPhase');
                    numEl = document.getElementById('sbNum');
                    guide = document.getElementById('sbGuide');
                    remainEl = document.getElementById('sbRemain');
                    startB = document.getElementById('sbStart');
                    resetB = document.getElementById('sbReset');
                    startB.addEventListener('click', doStart);
                    resetB.addEventListener('click', doReset);
                }
                mount();
                return function () { clearInterval(timer); };
            }
        },

        /* 3. 감사 일기 */
        gratitude: {
            title: '감사 일기',
            meta : '약 5분 · 기록 활동',
            render: function (el) {
                el.innerHTML =
                    '<div class="act-gratitude">' +
                    '<p class="muted" style="margin-bottom:1.25rem;">오늘 감사한 일 세 가지를 적어보세요.<br>작은 것도 충분히 가치 있어요.</p>' +
                    '<div id="gForm">' +
                    '<div class="form-group"><label for="g1">1번째 감사한 일</label>' +
                    '<input class="form-input" id="g1" placeholder="예: 오늘 날씨가 맑았다"></div>' +
                    '<div class="form-group"><label for="g2">2번째 감사한 일</label>' +
                    '<input class="form-input" id="g2" placeholder="예: 맛있는 점심을 먹었다"></div>' +
                    '<div class="form-group"><label for="g3">3번째 감사한 일</label>' +
                    '<input class="form-input" id="g3" placeholder="예: 좋아하는 음악을 들었다"></div>' +
                    '<button id="gSubmit" class="btn btn-primary btn-block" style="margin-top:0.5rem;">기록 완료</button>' +
                    '</div>' +
                    '<div id="gResult" style="display:none;"></div>' +
                    '</div>';

                document.getElementById('gSubmit').addEventListener('click', function () {
                    var vals = ['g1','g2','g3']
                        .map(function (id) { return document.getElementById(id).value.trim(); })
                        .filter(Boolean);
                    if (!vals.length) { document.getElementById('g1').focus(); return; }
                    var items = vals.map(function (t, i) {
                        return '<div class="grat-item">' +
                               '<span class="grat-num">' + (i + 1) + '</span>' +
                               '<span>' + esc(t) + '</span></div>';
                    }).join('');
                    var res = document.getElementById('gResult');
                    res.innerHTML =
                        '<div class="grat-card">' +
                        '<h4 style="color:var(--primary);margin:0 0 0.75rem;">오늘의 감사 목록</h4>' +
                        items + '</div>' +
                        '<p class="muted center" style="margin:1rem 0;font-size:0.9rem;">감사한 마음을 기록했어요. 오늘도 수고하셨습니다.</p>' +
                        '<button id="gAgain" class="btn btn-outline btn-block">다시 작성하기</button>';
                    res.style.display = 'block';
                    document.getElementById('gForm').style.display = 'none';
                    log('gratitude', { items: vals });
                    document.getElementById('gAgain').addEventListener('click', function () {
                        res.style.display = 'none'; res.innerHTML = '';
                        var form = document.getElementById('gForm');
                        form.style.display = 'block';
                        form.querySelectorAll('input').forEach(function (i) { i.value = ''; });
                        document.getElementById('g1').focus();
                    });
                });
            }
        },

        /* 4. PMR 미니 (짧은 점진적 근육 이완) */
        pmr_mini: {
            title: 'PMR 미니',
            meta : '약 5분 · 신체 이완',
            render: function (el) {
                var STEPS = [
                    { name: '양손·팔', tense: 5, relax: 8, tip: '두 손을 주먹 쥐고 팔에 힘을 줬다 풀어요' },
                    { name: '어깨·목', tense: 5, relax: 8, tip: '어깨를 귀 쪽으로 올렸다 툭 내려놓아요' },
                    { name: '얼굴',    tense: 5, relax: 8, tip: '눈과 얼굴을 살짝 찡그렸다 부드럽게 펴요' },
                    { name: '다리·발', tense: 5, relax: 8, tip: '다리와 발에 힘을 줬다 천천히 풀어요' }
                ];
                var si = 0, phase = 'idle', sLeft = 0, timer = null;

                function renderView() {
                    var s = STEPS[si];
                    var pct = Math.round((si / STEPS.length) * 100);
                    var isIdle = phase === 'idle';
                    var phLabel = phase === 'tense' ? '긴장' : phase === 'relax' ? '이완' : '';
                    var phCls   = phase === 'tense' ? 'pmr-tense' : phase === 'relax' ? 'pmr-relax' : '';
                    el.innerHTML =
                        '<div class="pmr-wrap">' +
                        '<div class="pmr-progress-bar"><div class="pmr-progress-fill" style="width:' + pct + '%"></div></div>' +
                        '<p class="pmr-step-count muted">' + (si + 1) + ' / ' + STEPS.length + ' &mdash; ' + esc(s.name) + '</p>' +
                        '<div class="pmr-circle-wrap"><div class="pmr-circle ' + phCls + '">' +
                        (isIdle
                            ? '<span class="pmr-circle-label">준비</span>'
                            : '<span class="pmr-circle-label">' + phLabel + '</span><span class="pmr-circle-num" id="pmrNum">' + sLeft + '</span>') +
                        '</div></div>' +
                        '<p class="act-guide pmr-tip">' + esc(s.tip) + '</p>' +
                        (isIdle ? '<button id="pmrGo" class="btn btn-primary btn-block">시작</button>' : '') +
                        '</div>';

                    if (isIdle) {
                        document.getElementById('pmrGo').addEventListener('click', startTense);
                    }
                }

                function startTense() {
                    phase = 'tense'; sLeft = STEPS[si].tense;
                    renderView();
                    timer = setInterval(tick, 1000);
                }

                function tick() {
                    sLeft--;
                    var numEl = document.getElementById('pmrNum');
                    if (sLeft > 0) { if (numEl) numEl.textContent = sLeft; return; }
                    clearInterval(timer);
                    if (phase === 'tense') {
                        phase = 'relax'; sLeft = STEPS[si].relax;
                        renderView();
                        timer = setInterval(tick, 1000);
                    } else {
                        si++;
                        if (si >= STEPS.length) { renderDone(); }
                        else { phase = 'idle'; renderView(); }
                    }
                }

                function renderDone() {
                    log('pmr_mini', null);
                    el.innerHTML =
                        '<div class="pmr-done">' +
                        '<p style="font-size:2rem;">🌿</p>' +
                        '<p style="line-height:1.75;margin:0.5rem 0;">짧게 몸을 풀어봤어요.<br>한결 가벼워졌기를 바라요.</p>' +
                        '<button id="pmrAgain" class="btn btn-outline btn-block" style="margin-top:1rem;">처음부터 다시</button></div>';
                    document.getElementById('pmrAgain').addEventListener('click', function () {
                        si = 0; phase = 'idle'; renderView();
                    });
                }

                renderView();
                return function () { clearInterval(timer); };
            }
        },

        /* 5. 자기자비 문장 */
        self_compassion_script: {
            title: '자기자비 문장',
            meta : '약 5분 · 인지',
            render: function (el) {
                var CARDS = [
                    '지금 힘든 것도 괜찮아요.',
                    '누구나 가끔은 버거울 수 있어요.',
                    '오늘은 여기까지 해도 충분해요.',
                    '나에게 조금만 관대해져도 돼요.',
                    '이 감정은 지나갈 수 있어요.'
                ];
                var idx = 0, logged = false;

                function render() {
                    el.innerHTML =
                        '<div class="scs-wrap">' +
                        '<div class="scs-card"><p class="scs-text">&ldquo;' + esc(CARDS[idx]) + '&rdquo;</p></div>' +
                        '<span class="scs-counter muted">' + (idx + 1) + ' / ' + CARDS.length + '</span>' +
                        '<p class="scs-hint muted">천천히, 나에게 건네듯 읽어보세요.</p>' +
                        '<div class="scs-nav">' +
                        '<button id="scsPrev" class="btn btn-outline btn-sm"' + (idx === 0 ? ' disabled' : '') + '>◀ 이전</button>' +
                        '<button id="scsNext" class="btn btn-primary btn-sm"' + (idx === CARDS.length - 1 ? ' disabled' : '') + '>다음 ▶</button>' +
                        '</div></div>';
                    var prev = document.getElementById('scsPrev');
                    var next = document.getElementById('scsNext');
                    prev.addEventListener('click', function () { if (idx > 0) { idx--; render(); } });
                    next.addEventListener('click', function () {
                        if (idx < CARDS.length - 1) {
                            idx++; render();
                            if (idx === CARDS.length - 1 && !logged) { logged = true; log('self_compassion_script', null); }
                        }
                    });
                }
                render();
            }
        },

        /* 6. 감정 체크인 */
        checkin: {
            title: '감정 체크인',
            meta : '약 2분 · 자기 인식',
            render: function (el) {
                var MOODS = [
                    { e: '😊', label: '행복해요',  v: 5, msg: '오늘 기분이 좋으시군요! 그 에너지를 주변과 나눠보세요.' },
                    { e: '😌', label: '평온해요',  v: 4, msg: '마음이 고요하고 편안한 상태네요. 이 평화로움을 즐겨보세요.' },
                    { e: '😐', label: '그냥 그래요', v: 3, msg: '평범한 하루도 충분히 괜찮아요. 작은 즐거움을 찾아보세요.' },
                    { e: '😔', label: '우울해요',  v: 2, msg: '마음이 무거우시군요. 억지로 나아지려 하지 않아도 괜찮아요. 지금 이 감정을 인정해드려요.' },
                    { e: '😰', label: '불안해요',  v: 2, msg: '불안한 마음이 드시는군요. 천천히 숨을 고르며 호흡 활동을 시도해보시겠어요?' },
                    { e: '😤', label: '화가 나요',  v: 2, msg: '화가 나는 감정도 자연스러워요. 잠시 심호흡을 해보시거나, 감정을 글로 표현해보세요.' },
                    { e: '😪', label: '피곤해요',  v: 2, msg: '많이 지치셨군요. 오늘 충분히 쉬어가셔도 괜찮아요.' },
                    { e: '🤗', label: '감사해요',  v: 5, msg: '감사함을 느끼고 계시군요! 감사 일기 활동도 시도해보시겠어요?' }
                ];
                var btns = MOODS.map(function (m, i) {
                    return '<button class="chk-btn" data-i="' + i + '">' +
                        '<span class="chk-emoji">' + m.e + '</span>' +
                        '<span class="chk-label">' + esc(m.label) + '</span></button>';
                }).join('');
                el.innerHTML =
                    '<div class="chk-wrap">' +
                    '<p class="chk-question">지금 이 순간, 당신의 기분은 어떤가요?</p>' +
                    '<div class="chk-grid">' + btns + '</div>' +
                    '<div id="chkResult"></div></div>';

                el.querySelectorAll('.chk-btn').forEach(function (btn) {
                    btn.addEventListener('click', function () {
                        el.querySelectorAll('.chk-btn').forEach(function (b) { b.classList.remove('selected'); });
                        btn.classList.add('selected');
                        var m = MOODS[parseInt(btn.dataset.i, 10)];
                        document.getElementById('chkResult').innerHTML =
                            '<div class="chk-result">' +
                            '<span class="chk-result-emoji">' + m.e + '</span>' +
                            '<p class="chk-result-msg">' + esc(m.msg) + '</p></div>';
                        log('checkin', { mood: m.label, emoji: m.e }, m.v);
                    });
                });
            }
        },

        /* 7. 점진적 근육 이완 (PMR) */
        pmr: {
            title: '점진적 근육 이완',
            meta : '약 10분 · 신체 활동',
            render: function (el) {
                var STEPS = [
                    { name: '양손',      tense: 5, relax: 10, tip: '두 손을 꽉 쥐어 주먹을 만드세요' },
                    { name: '팔뚝·위팔', tense: 5, relax: 10, tip: '팔을 구부려 이두근에 힘을 주세요' },
                    { name: '어깨',      tense: 5, relax: 10, tip: '어깨를 귀 쪽으로 최대한 올려보세요' },
                    { name: '얼굴',      tense: 5, relax: 10, tip: '눈을 감고 얼굴 근육 전체를 찡그리세요' },
                    { name: '가슴·배',   tense: 5, relax: 10, tip: '깊게 숨을 들이마시고 복근에 힘을 주세요' },
                    { name: '다리',      tense: 5, relax: 10, tip: '허벅지·종아리에 힘을 주고 발가락을 위로 당기세요' },
                    { name: '발',        tense: 5, relax: 10, tip: '발가락을 아래로 꽉 구부리세요' }
                ];
                var si = 0, phase = 'idle', sLeft = 0, timer = null;

                function renderView() {
                    var s = STEPS[si];
                    var pct = Math.round((si / STEPS.length) * 100);
                    var isIdle = phase === 'idle';
                    var phLabel = phase === 'tense' ? '긴장' : phase === 'relax' ? '이완' : '';
                    var phCls   = phase === 'tense' ? 'pmr-tense' : phase === 'relax' ? 'pmr-relax' : '';
                    el.innerHTML =
                        '<div class="pmr-wrap">' +
                        '<div class="pmr-progress-bar"><div class="pmr-progress-fill" style="width:' + pct + '%"></div></div>' +
                        '<p class="pmr-step-count muted">' + (si + 1) + ' / ' + STEPS.length + ' &mdash; ' + esc(s.name) + '</p>' +
                        '<div class="pmr-circle-wrap"><div class="pmr-circle ' + phCls + '">' +
                        (isIdle
                            ? '<span class="pmr-circle-label">준비</span>'
                            : '<span class="pmr-circle-label">' + phLabel + '</span><span class="pmr-circle-num" id="pmrNum">' + sLeft + '</span>') +
                        '</div></div>' +
                        '<p class="act-guide pmr-tip">' + esc(s.tip) + '</p>' +
                        (isIdle ? '<button id="pmrGo" class="btn btn-primary btn-block">시작</button>' : '') +
                        '</div>';

                    if (isIdle) {
                        document.getElementById('pmrGo').addEventListener('click', startTense);
                    }
                }

                function startTense() {
                    phase = 'tense'; sLeft = STEPS[si].tense;
                    renderView();
                    timer = setInterval(tick, 1000);
                }

                function tick() {
                    sLeft--;
                    var numEl = document.getElementById('pmrNum');
                    if (sLeft > 0) { if (numEl) numEl.textContent = sLeft; return; }
                    clearInterval(timer);
                    if (phase === 'tense') {
                        phase = 'relax'; sLeft = STEPS[si].relax;
                        renderView();
                        timer = setInterval(tick, 1000);
                    } else {
                        si++;
                        if (si >= STEPS.length) { renderDone(); }
                        else { phase = 'idle'; renderView(); }
                    }
                }

                function renderDone() {
                    log('pmr', null);
                    el.innerHTML =
                        '<div class="pmr-done">' +
                        '<p style="font-size:2rem;">🌿</p>' +
                        '<p style="line-height:1.75;margin:0.5rem 0;">온몸의 긴장이 풀렸나요?<br>충분히 이완되셨기를 바랍니다.</p>' +
                        '<button id="pmrAgain" class="btn btn-outline btn-block" style="margin-top:1rem;">처음부터 다시</button></div>';
                    document.getElementById('pmrAgain').addEventListener('click', function () {
                        si = 0; phase = 'idle'; renderView();
                    });
                }

                renderView();
                return function () { clearInterval(timer); };
            }
        },

        /* 8. 짧은 바디스캔 */
        bodyscan: {
            title: '짧은 바디스캔',
            meta : '약 10분 · 명상',
            render: function (el) {
                var STEPS = [
                    { name: '발',        dur: 40, guide: '발바닥, 발뒤꿈치, 발가락 사이의 감각을 느껴보세요.' },
                    { name: '다리',      dur: 40, guide: '종아리, 정강이, 무릎, 허벅지로 천천히 주의를 옮겨보세요.' },
                    { name: '골반·엉덩이', dur: 30, guide: '골반과 엉덩이 부위를 느껴보세요. 바닥과의 접촉감을 인식하세요.' },
                    { name: '배·허리',   dur: 30, guide: '숨을 쉴 때 배가 올라가고 내려오는 것을 느껴보세요.' },
                    { name: '가슴',      dur: 30, guide: '심장이 뛰는 것을 느껴보세요. 가슴이 확장되고 수축하는 것을 인식하세요.' },
                    { name: '손·팔',     dur: 30, guide: '손끝부터 손목, 팔꿈치, 어깨까지 천천히 주의를 이동하세요.' },
                    { name: '어깨·목',   dur: 30, guide: '어깨의 긴장을 느껴보세요. 목을 천천히 살펴보세요.' },
                    { name: '얼굴·머리', dur: 40, guide: '이마, 눈 주변, 볼, 턱을 살펴보세요. 두피까지 주의를 확장해보세요.' },
                    { name: '전신',      dur: 30, guide: '온몸 전체를 한 번에 느껴보세요. 이 순간, 여기에 온전히 존재하세요.' }
                ];
                var si = 0, sLeft = 0, timer = null, running = false;

                function renderStep(idx) {
                    var s = STEPS[idx];
                    var pct = Math.round((idx / STEPS.length) * 100);
                    el.innerHTML =
                        '<div class="bds-wrap">' +
                        '<div class="bds-progress"><div class="bds-fill" style="width:' + pct + '%"></div></div>' +
                        '<p class="muted bds-count">' + (idx + 1) + ' / ' + STEPS.length + '</p>' +
                        '<div class="bds-body-label">' + esc(s.name) + '</div>' +
                        '<div class="bds-timer-wrap"><div class="bds-ring"><span id="bdsNum">' + (running ? sLeft : s.dur) + '</span></div></div>' +
                        '<p class="bds-guide">' + esc(s.guide) + '</p>' +
                        (!running ? '<button id="bdsGo" class="btn btn-primary btn-block">시작</button>' : '') +
                        '</div>';

                    if (!running) {
                        document.getElementById('bdsGo').addEventListener('click', function () {
                            running = true; sLeft = s.dur;
                            renderStep(idx);
                            timer = setInterval(tick, 1000);
                        });
                    }
                }

                function tick() {
                    sLeft--;
                    var numEl = document.getElementById('bdsNum');
                    if (sLeft > 0) { if (numEl) numEl.textContent = sLeft; return; }
                    clearInterval(timer);
                    running = false;
                    si++;
                    if (si >= STEPS.length) renderDone();
                    else renderStep(si);
                }

                function renderDone() {
                    log('bodyscan', null);
                    el.innerHTML =
                        '<div class="bds-done">' +
                        '<p style="font-size:2rem;">🧘</p>' +
                        '<p style="line-height:1.75;margin:0.5rem 0;">바디스캔이 완료되었습니다.<br>온몸의 감각을 느끼며 잠시 머물러 보세요.</p>' +
                        '<button id="bdsAgain" class="btn btn-outline btn-block" style="margin-top:1rem;">처음부터 다시</button></div>';
                    document.getElementById('bdsAgain').addEventListener('click', function () {
                        si = 0; running = false; renderStep(0);
                    });
                }

                renderStep(0);
                return function () { clearInterval(timer); };
            }
        },

        /* 9. 5-4-3-2-1 그라운딩 */
        grounding: {
            title: '5-4-3-2-1 그라운딩',
            meta : '약 3분 · 자기 인식',
            render: function (el) {
                var STEPS = [
                    { n: 5, sense: '보이는 것',   txt: '지금 눈에 보이는 것 다섯 가지를 천천히 찾아보세요.', ph: '예: 창밖의 나무' },
                    { n: 4, sense: '들리는 소리', txt: '들려오는 소리 네 가지에 가만히 귀를 기울여요.',     ph: '예: 시계 초침 소리' },
                    { n: 3, sense: '만져지는 것', txt: '몸에 닿거나 만질 수 있는 것 세 가지를 느껴보세요.', ph: '예: 의자의 감촉' },
                    { n: 2, sense: '나는 냄새',   txt: '맡을 수 있는 냄새 두 가지를 찾아보세요.',           ph: '예: 커피 향' },
                    { n: 1, sense: '느껴지는 맛', txt: '입안의 맛, 또는 좋아하는 맛 한 가지를 떠올려요.',   ph: '예: 물의 맛' }
                ];
                var si = 0, answers = [];

                function saveCurrent(idx) {
                    var vals = [];
                    el.querySelectorAll('.grd-input').forEach(function (inp) {
                        vals[parseInt(inp.dataset.i, 10)] = inp.value.trim();
                    });
                    answers[idx] = vals;
                }

                function renderStep(idx) {
                    var s = STEPS[idx];
                    var pct = Math.round((idx / STEPS.length) * 100);
                    var inputs = '';
                    for (var i = 0; i < s.n; i++) {
                        inputs += '<input class="form-input grd-input" data-i="' + i +
                                  '" placeholder="' + esc(s.ph) + '">';
                    }
                    el.innerHTML =
                        '<div class="grd-wrap">' +
                        '<div class="pmr-progress-bar"><div class="pmr-progress-fill" style="width:' + pct + '%"></div></div>' +
                        '<p class="pmr-step-count muted">' + (idx + 1) + ' / ' + STEPS.length + '</p>' +
                        '<div class="grd-badge">' + s.n + '</div>' +
                        '<h3 class="grd-sense">' + esc(s.sense) + '</h3>' +
                        '<p class="act-guide">' + esc(s.txt) + '</p>' +
                        '<div class="grd-inputs">' + inputs + '</div>' +
                        '<div class="act-btns">' +
                        (idx > 0 ? '<button id="grdPrev" class="btn btn-outline">이전</button>' : '') +
                        '<button id="grdNext" class="btn btn-primary">' +
                        (idx === STEPS.length - 1 ? '완료' : '다음') + '</button>' +
                        '</div></div>';

                    var prevB = document.getElementById('grdPrev');
                    if (prevB) {
                        prevB.addEventListener('click', function () {
                            saveCurrent(idx); si--; renderStep(si);
                        });
                    }
                    document.getElementById('grdNext').addEventListener('click', function () {
                        saveCurrent(idx);
                        if (idx === STEPS.length - 1) { renderDone(); }
                        else { si++; renderStep(si); }
                    });

                    if (answers[idx]) {
                        el.querySelectorAll('.grd-input').forEach(function (inp) {
                            var v = answers[idx][parseInt(inp.dataset.i, 10)];
                            if (v) inp.value = v;
                        });
                    }
                    var first = el.querySelector('.grd-input');
                    if (first) first.focus();
                }

                function renderDone() {
                    var flat = [];
                    answers.forEach(function (a) {
                        (a || []).forEach(function (v) { if (v) flat.push(v); });
                    });
                    log('grounding', flat.length ? { items: flat } : null);
                    el.innerHTML =
                        '<div class="pmr-done">' +
                        '<p style="font-size:2rem;">⚓</p>' +
                        '<p style="line-height:1.75;margin:0.5rem 0;">지금 이 순간으로 돌아왔어요.<br>한결 차분해지셨기를 바라요.</p>' +
                        '<button id="grdAgain" class="btn btn-outline btn-block" style="margin-top:1rem;">처음부터 다시</button></div>';
                    document.getElementById('grdAgain').addEventListener('click', function () {
                        si = 0; answers = []; renderStep(0);
                    });
                }

                renderStep(0);
            }
        },

        /* 10. 생각 적어내기 (걱정 비우기) */
        thought_dump: {
            title: '생각 적어내기',
            meta : '약 5분 · 인지',
            render: function (el) {
                function renderWrite() {
                    el.innerHTML =
                        '<div class="td-wrap">' +
                        '<p class="muted" style="margin-bottom:1rem;line-height:1.7;">머릿속을 맴도는 걱정이나 생각을 떠오르는 대로 적어보세요.<br>잘 쓰려 하지 않아도 괜찮아요. 꺼내 놓는 것만으로 충분해요.</p>' +
                        '<textarea id="tdText" class="form-textarea" placeholder="지금 마음에 걸리는 것들을 자유롭게 적어보세요…" style="min-height:8rem;"></textarea>' +
                        '<button id="tdNext" class="btn btn-primary btn-block" style="margin-top:0.75rem;">다 적었어요</button>' +
                        '</div>';
                    document.getElementById('tdNext').addEventListener('click', renderReframe);
                    document.getElementById('tdText').focus();
                }
                function renderReframe() {
                    el.innerHTML =
                        '<div class="td-wrap">' +
                        '<p class="act-guide" style="text-align:left;">잘 꺼내 놓으셨어요. 마지막으로, 적은 것 중 <b>지금 내가 할 수 있는 작은 한 가지</b>가 떠오른다면 적어보세요. 없다면 비워 두어도 괜찮아요.</p>' +
                        '<input id="tdAction" class="form-input" placeholder="예: 내일 아침에 메일 한 통 보내기">' +
                        '<div class="act-btns" style="margin-top:0.75rem;">' +
                        '<button id="tdBack" class="btn btn-outline">이전</button>' +
                        '<button id="tdDone" class="btn btn-primary">내려놓기</button>' +
                        '</div></div>';
                    document.getElementById('tdBack').addEventListener('click', renderWrite);
                    document.getElementById('tdDone').addEventListener('click', function () {
                        var action = document.getElementById('tdAction').value.trim();
                        log('thought_dump', action ? { items: [action] } : null);
                        renderDone();
                    });
                    document.getElementById('tdAction').focus();
                }
                function renderDone() {
                    el.innerHTML =
                        '<div class="pmr-done">' +
                        '<p style="font-size:2rem;">🍃</p>' +
                        '<p style="line-height:1.75;margin:0.5rem 0;">머릿속 생각을 한결 비워냈어요.<br>적어 둔 걱정은 잠시 내려놓아도 괜찮아요.</p>' +
                        '<button id="tdAgain" class="btn btn-outline btn-block" style="margin-top:1rem;">다시 하기</button></div>';
                    document.getElementById('tdAgain').addEventListener('click', renderWrite);
                }
                renderWrite();
            }
        },

        /* 11. 나비 포옹 */
        butterfly_hug: {
            title: '나비 포옹',
            meta : '약 2분 · 정서 안정',
            render: function (el) {
                var DURATION = 60;    // 진행 시간(초)
                var SWAP_MS  = 900;   // 좌↔우 전환 간격
                var side = 0, secLeft = DURATION, swapTimer = null, countTimer = null;

                function paint() {
                    var l = document.getElementById('bfL'), r = document.getElementById('bfR');
                    if (l) l.classList.toggle('active', side === 0);
                    if (r) r.classList.toggle('active', side === 1);
                }
                function stop() { clearInterval(swapTimer); clearInterval(countTimer); }

                function renderIntro() {
                    el.innerHTML =
                        '<div class="act-wrap">' +
                        '<p class="act-guide" style="line-height:1.8;">양손을 가슴 위에 교차해 나비 모양으로 얹어 주세요.<br>화면이 알려주는 쪽을 천천히, 번갈아 토닥입니다.</p>' +
                        '<div class="bf-sides">' +
                        '<div class="bf-side" id="bfL">왼쪽</div>' +
                        '<div class="bf-side" id="bfR">오른쪽</div>' +
                        '</div>' +
                        '<p class="bf-count muted" id="bfCount">약 1분간 진행돼요</p>' +
                        '<div class="act-btns"><button id="bfStart" class="btn btn-primary">시작</button></div>' +
                        '</div>';
                    document.getElementById('bfStart').addEventListener('click', start);
                }
                function start() {
                    var startB = document.getElementById('bfStart');
                    if (startB) { startB.disabled = true; startB.textContent = '진행 중…'; }
                    side = 0; secLeft = DURATION; paint();
                    swapTimer  = setInterval(function () { side = side ? 0 : 1; paint(); }, SWAP_MS);
                    countTimer = setInterval(function () {
                        secLeft--;
                        var c = document.getElementById('bfCount');
                        if (c) c.textContent = secLeft + '초 남았어요';
                        if (secLeft <= 0) { stop(); renderDone(); }
                    }, 1000);
                }
                function renderDone() {
                    log('butterfly_hug', null);
                    el.innerHTML =
                        '<div class="pmr-done">' +
                        '<p style="font-size:2rem;">🦋</p>' +
                        '<p style="line-height:1.75;margin:0.5rem 0;">스스로를 충분히 토닥였어요.<br>호흡을 고르며 잠시 머물러 보세요.</p>' +
                        '<button id="bfAgain" class="btn btn-outline btn-block" style="margin-top:1rem;">다시 하기</button></div>';
                    document.getElementById('bfAgain').addEventListener('click', renderIntro);
                }
                renderIntro();
                return function () { stop(); };
            }
        },

        /* 12. 작은 실천 한 가지 (행동 활성화) */
        small_action: {
            title: '작은 실천 한 가지',
            meta : '약 2분 · 행동 활성화',
            render: function (el) {
                var IDEAS = [
                    '물 한 잔 마시기', '창문 열고 환기하기', '가볍게 어깨 펴기',
                    '좋아하는 노래 한 곡 듣기', '문 밖으로 5분 산책', '반가운 사람에게 안부 한 줄',
                    '책상 위 한 곳 정리', '따뜻한 차 한 잔'
                ];
                var selected = null;

                function renderPick() {
                    var chips = IDEAS.map(function (t, i) {
                        return '<button class="sa-chip" data-i="' + i + '">' + esc(t) + '</button>';
                    }).join('');
                    el.innerHTML =
                        '<div class="sa-wrap">' +
                        '<p class="muted" style="margin-bottom:1rem;line-height:1.7;">지금 부담 없이 할 수 있는 <b>아주 작은 행동</b> 하나를 골라보세요.<br>직접 적어도 좋아요.</p>' +
                        '<div class="sa-chips">' + chips + '</div>' +
                        '<input id="saCustom" class="form-input" placeholder="직접 적기" style="margin-top:0.75rem;">' +
                        '<button id="saGo" class="btn btn-primary btn-block" style="margin-top:0.75rem;" disabled>이거 해볼래요</button>' +
                        '</div>';

                    var goBtn  = document.getElementById('saGo');
                    var custom = document.getElementById('saCustom');

                    el.querySelectorAll('.sa-chip').forEach(function (chip) {
                        chip.addEventListener('click', function () {
                            el.querySelectorAll('.sa-chip').forEach(function (c) { c.classList.remove('selected'); });
                            chip.classList.add('selected');
                            selected = IDEAS[parseInt(chip.dataset.i, 10)];
                            custom.value = '';
                            goBtn.disabled = false;
                        });
                    });
                    custom.addEventListener('input', function () {
                        var v = custom.value.trim();
                        if (v) {
                            el.querySelectorAll('.sa-chip').forEach(function (c) { c.classList.remove('selected'); });
                            selected = v;
                            goBtn.disabled = false;
                        } else if (!el.querySelector('.sa-chip.selected')) {
                            selected = null;
                            goBtn.disabled = true;
                        }
                    });
                    goBtn.addEventListener('click', function () {
                        var choice = custom.value.trim() || selected;
                        if (!choice) return;
                        log('small_action', { items: [choice] });
                        renderDone(choice);
                    });
                }
                function renderDone(choice) {
                    el.innerHTML =
                        '<div class="pmr-done">' +
                        '<p style="font-size:2rem;">🌱</p>' +
                        '<p style="line-height:1.75;margin:0.5rem 0;">좋아요, <b>' + esc(choice) + '</b>.<br>거창하지 않아도 괜찮아요. 지금 한 번 해볼까요?</p>' +
                        '<button id="saAgain" class="btn btn-outline btn-block" style="margin-top:1rem;">다른 것 고르기</button></div>';
                    document.getElementById('saAgain').addEventListener('click', function () {
                        selected = null; renderPick();
                    });
                }
                renderPick();
            }
        }
    };

    /** 활동을 host 요소에 렌더링. 정리(cleanup) 함수 반환. */
    function run(key, host) {
        var def = ACTS[key];
        if (!def || !host) return null;
        host.innerHTML = '';
        return def.render(host) || null;
    }

    return {
        ACTS: ACTS,
        run: run,
        log: log,
        setOnLogged: function (fn) { onLogged = fn; },
        setAskBeforeSave: function (v) { askBeforeSave = !!v; }
    };
})();
