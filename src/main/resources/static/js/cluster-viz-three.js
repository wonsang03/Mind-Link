/**
 * 정서 3D 클러스터 맵 — Three.js WebGL 산점도
 *
 * 구역 인덱스
 *   §0. 상수·DOM·전역 상태
 *   §1. 헬퍼 (색·좌표 변환·escape)
 *   §2. 씬 초기화 (initThree, buildGuide, makeSphereMesh)
 *   §3. dispose / clear (공유 geometry 보호)
 *   §4. 점·centroid 렌더링
 *   §5. 하이라이트·포커스 (focusOnProfile 등)
 *   §6. 검색 (searchUsers, renderSearchResults)
 *   §7. 카메라·줌·리사이즈
 *   §8. 포인터·툴팁
 *   §9. 데이터 로드 (visualization / my / recompute)
 *   §10. UI 이벤트 바인딩 + 부트스트랩
 */
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

// ═════════════════════════════════════════════════════════════════════
// §0. 상수 · DOM · 전역 상태
// ═════════════════════════════════════════════════════════════════════

const CLUSTER_HEX = [
    0x4a90c4, 0xd97706, 0x6366f1, 0xdb2777,
    0xdc2626, 0x65a30d, 0x0d9488, 0x9333ea,
];

const VIEWPORT = document.getElementById('viz-viewport');
const CANVAS = document.getElementById('viz-canvas');
const TOOLTIP = document.getElementById('viz-tooltip');
const ERR = document.getElementById('viz-error');
const LEGEND = document.getElementById('legend');
const MY = document.getElementById('my-cluster');
const OPT_SYN = document.getElementById('opt-synthetic');
const OPT_REAL = document.getElementById('opt-real');
const OPT_CENTROID = document.getElementById('opt-centroid');
const ROT_X = document.getElementById('rotX');
const ROT_Y = document.getElementById('rotY');
const SEARCH_INPUT = document.getElementById('user-search-input');
const SEARCH_RESULTS = document.getElementById('search-results');

const ZOOM_MIN = 0.55;
const ZOOM_MAX = 2.8;
const ZOOM_STEP = 0.1;
const BASE_DISTANCE = 4.8;

let scene, camera, renderer, controls, raycaster, pointer;
let pointsGroup, centroidsGroup, guideGroup;
let pointEntries = [];
let centroidEntries = [];
let DATA = null;
let highlightCluster = null;
let focusedProfileId = null;
let focusAnim = null;
let zoom = 1.0;
let spinTimer = null;
let hovered = null;
let animId = null;
let sharedSphereGeo = null;
let sharedRealGeo = null;
let sharedSphereEdges = null;
let sharedRealEdges = null;

/** 산점도 점 크기 — 실사용자·페르소나 동일 스케일 (도형만 다름) */
const SIZE_POINT = 0.036;
const SIZE_ME = 0.042;
const SIZE_FOCUS_MUL = 1.22;
const SIZE_HOVER_MUL = 1.18;

// ═════════════════════════════════════════════════════════════════════
// §1. 헬퍼 — 색 매핑·좌표 변환·escape
// ═════════════════════════════════════════════════════════════════════

function clusterHex(cid) {
    if (cid == null || cid < 0) return 0x94a3b8;
    return CLUSTER_HEX[cid] ?? 0x94a3b8;
}

function clusterColorVar(cid) {
    return cid == null ? 'var(--cluster-unknown)' : 'var(--cluster-' + cid + ')';
}

function normToVec(s, d, a) {
    return new THREE.Vector3(
        (s ?? 0) * 2 - 1,
        (d ?? 0) * 2 - 1,
        (a ?? 0) * 2 - 1,
    );
}

function clamp(v, lo, hi) {
    return Math.max(lo, Math.min(hi, v));
}

function escapeHtml(s) {
    return String(s).replace(/[&<>"]/g, (c) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;',
    }[c]));
}

// ═════════════════════════════════════════════════════════════════════
// §2. 씬 초기화 — 카메라·렌더러·OrbitControls·조명·가이드 박스
// ═════════════════════════════════════════════════════════════════════

function initThree() {
    scene = new THREE.Scene();
    scene.background = new THREE.Color(0xf4f7f4);
    scene.fog = new THREE.Fog(0xf4f7f4, 8, 14);

    const rect = VIEWPORT.getBoundingClientRect();
    camera = new THREE.PerspectiveCamera(48, rect.width / rect.height, 0.1, 50);
    camera.position.set(2.6, 2.1, 2.9);

    renderer = new THREE.WebGLRenderer({
        canvas: CANVAS,
        antialias: true,
        alpha: false,
    });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.setSize(rect.width, rect.height, false);
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;

    controls = new OrbitControls(camera, CANVAS);
    controls.enableDamping = true;
    controls.dampingFactor = 0.08;
    controls.enableZoom = true;
    controls.minDistance = 2.2;
    controls.maxDistance = 11;
    controls.target.set(0, 0, 0);
    controls.update();

    CANVAS.addEventListener('pointerdown', () => VIEWPORT.classList.add('dragging'));
    CANVAS.addEventListener('pointerup', () => VIEWPORT.classList.remove('dragging'));
    CANVAS.addEventListener('pointerleave', () => VIEWPORT.classList.remove('dragging'));

    sharedSphereGeo = new THREE.SphereGeometry(1, 14, 14);
    sharedRealGeo = new THREE.OctahedronGeometry(1, 0);
    sharedSphereEdges = new THREE.EdgesGeometry(sharedSphereGeo);
    sharedRealEdges = new THREE.EdgesGeometry(sharedRealGeo);

    raycaster = new THREE.Raycaster();
    raycaster.params.Points = { threshold: 0.08 };
    pointer = new THREE.Vector2();

    const ambient = new THREE.AmbientLight(0xffffff, 0.55);
    scene.add(ambient);

    const key = new THREE.DirectionalLight(0xffffff, 0.9);
    key.position.set(4, 6, 3);
    key.castShadow = true;
    key.shadow.mapSize.set(1024, 1024);
    scene.add(key);

    const fill = new THREE.DirectionalLight(0xe8f0ea, 0.35);
    fill.position.set(-3, 2, -4);
    scene.add(fill);

    guideGroup = buildGuide();
    scene.add(guideGroup);

    pointsGroup = new THREE.Group();
    centroidsGroup = new THREE.Group();
    centroidsGroup.renderOrder = 0;
    pointsGroup.renderOrder = 10;
    scene.add(centroidsGroup);
    scene.add(pointsGroup);

    setCameraFromSliders(false);
    syncSlidersFromCamera();

    controls.addEventListener('change', () => {
        syncSlidersFromCamera();
        updateZoomLabel();
    });

    window.addEventListener('resize', onResize);
    CANVAS.addEventListener('mousemove', onPointerMove);
    CANVAS.addEventListener('mouseleave', hideTooltip);

    function tick() {
        animId = requestAnimationFrame(tick);
        controls.update();
        renderer.render(scene, camera);
    }
    tick();
}

function buildGuide() {
    const g = new THREE.Group();

    const box = new THREE.BoxGeometry(2, 2, 2);
    const edges = new THREE.EdgesGeometry(box);
    const wire = new THREE.LineSegments(
        edges,
        new THREE.LineBasicMaterial({ color: 0x6b8e6f, transparent: true, opacity: 0.55 }),
    );
    g.add(wire);

    const grid = new THREE.GridHelper(2, 10, 0x9cb8a0, 0xdce8de);
    grid.position.y = -1.001;
    g.add(grid);

    const floor = new THREE.Mesh(
        new THREE.PlaneGeometry(2.2, 2.2),
        new THREE.MeshLambertMaterial({ color: 0xeef3ee, transparent: true, opacity: 0.85 }),
    );
    floor.rotation.x = -Math.PI / 2;
    floor.position.y = -1.002;
    floor.receiveShadow = true;
    g.add(floor);

    const axes = new THREE.AxesHelper(1.05);
    axes.material.opacity = 0.35;
    axes.material.transparent = true;
    g.add(axes);

    return g;
}

function makeSphereMesh(radius, color, emissive = 0x000000) {
    const geo = new THREE.SphereGeometry(radius, 20, 20);
    const mat = new THREE.MeshPhongMaterial({
        color,
        emissive,
        emissiveIntensity: 0.15,
        shininess: 48,
        transparent: true,
        opacity: 1,
    });
    const mesh = new THREE.Mesh(geo, mat);
    mesh.castShadow = true;
    mesh.receiveShadow = false;
    return mesh;
}

// ═════════════════════════════════════════════════════════════════════
// §3. dispose / clear — 공유 geometry 는 절대 dispose 하지 않는다
//   · clearGroup(group, disposeGeo) : centroid 처럼 새 geometry 가 매 렌더마다 생기는 그룹용
//   · clearPointsGroup()            : 점 그룹 — geometry 공유라 material 만 dispose
// ═════════════════════════════════════════════════════════════════════

function disposeObject3D(obj, disposeGeo) {
    obj.traverse((ch) => {
        if (disposeGeo && ch.geometry && !isSharedGeometry(ch.geometry)) ch.geometry.dispose();
        if (ch.material) {
            const mats = Array.isArray(ch.material) ? ch.material : [ch.material];
            mats.forEach((m) => m.dispose());
        }
    });
}

function isSharedGeometry(geo) {
    return geo === sharedSphereGeo || geo === sharedRealGeo
        || geo === sharedSphereEdges || geo === sharedRealEdges;
}

function clearGroup(group, disposeGeo) {
    while (group.children.length) {
        const ch = group.children[0];
        group.remove(ch);
        disposeObject3D(ch, disposeGeo);
    }
}

/** 점 그룹만 비우기 — 공유 geometry 는 유지 */
function clearPointsGroup() {
    while (pointsGroup.children.length) {
        const ch = pointsGroup.children[0];
        pointsGroup.remove(ch);
        ch.traverse((node) => {
            if (node.material) {
                const mats = Array.isArray(node.material) ? node.material : [node.material];
                mats.forEach((m) => m.dispose());
            }
        });
    }
}

// ═════════════════════════════════════════════════════════════════════
// §4. 점 · centroid 렌더링
//   · 페르소나 = 구(sphere), 실사용자 = 팔면체(octahedron) — 같은 크기·다른 도형
// ═════════════════════════════════════════════════════════════════════

function pointRadius(p) {
    return p.isMe ? SIZE_ME : SIZE_POINT;
}

/** 점 + 테두리(실사용자·포커스 시 강조) — 중심점에 가려지지 않게 그룹으로 관리 */
function createPointNode(p) {
    const pos = normToVec(p.stressNorm, p.depressionNorm, p.anxietyNorm);
    const isReal = !p.synthetic;
    const r = pointRadius(p);
    const color = clusterHex(p.clusterId);

    const group = new THREE.Group();
    group.position.copy(pos);
    group.userData.point = p;
    group.userData.isReal = isReal;
    group.userData._baseScale = r;

    const mat = new THREE.MeshPhongMaterial({
        color,
        emissive: color,
        emissiveIntensity: p.isMe ? 0.18 : 0.08,
        shininess: 40,
        transparent: true,
        opacity: 0.86,
        depthWrite: true,
    });
    const geo = isReal ? sharedRealGeo : sharedSphereGeo;
    const mesh = new THREE.Mesh(geo, mat);
    mesh.scale.setScalar(r);
    mesh.castShadow = !isReal;
    mesh.receiveShadow = true;
    group.add(mesh);

    const edgeGeo = isReal ? sharedRealEdges : sharedSphereEdges;
    const outlineMat = new THREE.LineBasicMaterial({
        color: isReal ? 0xffffff : 0x334155,
        transparent: true,
        opacity: isReal ? 0.55 : 0.2,
        depthTest: true,
        depthWrite: false,
    });
    const outline = new THREE.LineSegments(edgeGeo, outlineMat);
    outline.scale.setScalar(r * 1.1);
    outline.userData.isOutline = true;
    outline.renderOrder = 11;
    group.add(outline);

    group.renderOrder = 10;
    return { group, mesh, outline, p };
}

function renderPoints(points) {
    clearPointsGroup();
    pointEntries = [];

    points.forEach((p) => {
        const node = createPointNode(p);
        pointsGroup.add(node.group);
        pointEntries.push(node);
    });
}

function bringPointToFront(entry) {
    if (!entry?.group) return;
    pointsGroup.remove(entry.group);
    pointsGroup.add(entry.group);
    entry.group.renderOrder = 50;
}

function renderCentroids(centroids) {
    clearGroup(centroidsGroup, true);
    centroidEntries = [];
    if (!OPT_CENTROID.checked) return;

    centroids.forEach((c) => {
        const pos = normToVec(c.centroidS, c.centroidD, c.centroidA);
        const color = clusterHex(c.clusterId);
        const outer = new THREE.Mesh(
            new THREE.SphereGeometry(0.062, 14, 14),
            new THREE.MeshPhongMaterial({
                color: 0xffffff,
                transparent: true,
                opacity: 0.18,
                depthWrite: false,
            }),
        );
        const ring = new THREE.Mesh(
            new THREE.SphereGeometry(0.05, 14, 14),
            new THREE.MeshPhongMaterial({
                color,
                emissive: color,
                emissiveIntensity: 0.15,
                wireframe: true,
                transparent: true,
                opacity: 0.7,
                depthWrite: false,
            }),
        );
        const core = makeSphereMesh(0.032, color, color);
        core.material.opacity = 0.5;
        core.material.depthWrite = false;
        const g = new THREE.Group();
        g.position.copy(pos);
        g.renderOrder = 0;
        g.add(outer);
        g.add(ring);
        g.add(core);
        g.userData.centroid = c;
        centroidsGroup.add(g);
        centroidEntries.push({ group: g, c });
    });
}

// ═════════════════════════════════════════════════════════════════════
// §5. 하이라이트 · 포커스 — 범례 클릭 dim, 검색 결과 클릭 시 카메라 트윈
// ═════════════════════════════════════════════════════════════════════

function applyPointScale(entry, mul) {
    const r = (entry.group.userData._baseScale ?? SIZE_POINT) * mul;
    entry.mesh.scale.setScalar(r);
    if (entry.outline) entry.outline.scale.setScalar(r * 1.1);
}

function applyHighlight() {
    pointEntries.forEach((entry) => {
        const { mesh, outline, p, group } = entry;
        const clusterDim = highlightCluster != null && p.clusterId !== highlightCluster;
        const focusDim = focusedProfileId != null && p.id !== focusedProfileId;
        const dim = clusterDim || focusDim;
        const focused = focusedProfileId != null && p.id === focusedProfileId;

        mesh.material.opacity = dim ? 0.06 : (focused ? 0.98 : 0.86);
        mesh.material.emissiveIntensity = dim ? 0 : (focused ? 0.28 : (p.isMe ? 0.18 : 0.08));

        if (outline) {
            outline.material.opacity = dim ? 0.05 : (focused ? 1 : (entry.group.userData.isReal ? 0.55 : 0.2));
            outline.material.color.setHex(focused ? 0xffffff : (entry.group.userData.isReal ? 0xffffff : 0x334155));
            outline.renderOrder = focused ? 60 : 11;
        }

        group.renderOrder = focused ? 50 : 10;
        applyPointScale(entry, focused ? SIZE_FOCUS_MUL : 1);

        if (focused) bringPointToFront(entry);
    });
}

function focusOnProfile(profileId) {
    const entry = pointEntries.find((e) => e.p.id === profileId);
    if (!entry) {
        ERR.textContent = '3D 맵에 해당 사용자가 없어요. 「실사용자 보기」를 켠 뒤 새로고침해 주세요.';
        return;
    }
    ERR.textContent = '';
    focusedProfileId = profileId;
    applyHighlight();
    bringPointToFront(entry);

    const target = entry.group.position.clone();
    const endTarget = target.clone();
    const offset = camera.position.clone().sub(controls.target);
    if (offset.lengthSq() < 1e-6) offset.set(1, 1, 1);
    offset.setLength(2.1);
    const endPos = target.clone().add(offset);

    const startTarget = controls.target.clone();
    const startPos = camera.position.clone();
    let t = 0;
    if (focusAnim) cancelAnimationFrame(focusAnim);
    const step = () => {
        t += 0.06;
        const k = Math.min(1, t);
        const ease = 1 - Math.pow(1 - k, 3);
        controls.target.lerpVectors(startTarget, endTarget, ease);
        camera.position.lerpVectors(startPos, endPos, ease);
        controls.update();
        syncSlidersFromCamera();
        if (k < 1) focusAnim = requestAnimationFrame(step);
        else focusAnim = null;
    };
    step();

    const fakePoint = entry.p;
    showTooltip({ clientX: 0, clientY: 0 }, fakePoint);
    TOOLTIP.style.display = 'block';
    TOOLTIP.style.left = '12px';
    TOOLTIP.style.top = '12px';
}

// ═════════════════════════════════════════════════════════════════════
// §6. 검색 — /api/chat-cluster/search ?q= 호출 + 결과 클릭 시 포커스
// ═════════════════════════════════════════════════════════════════════

function renderSearchResults(data) {
    SEARCH_RESULTS.innerHTML = '';
    if (!data.matches || data.matches.length === 0) {
        SEARCH_RESULTS.innerHTML = '<p class="viz-search-empty">검색 결과가 없어요.</p>';
        return;
    }
    data.matches.forEach((m) => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'viz-search-item';
        btn.dataset.profileId = m.profileId;
        const cid = m.clusterId == null ? '—' : m.clusterId;
        btn.innerHTML = '<strong>' + escapeHtml(m.name) + '</strong>'
            + '<span class="meta">집단 ' + cid
            + ' · S' + Math.round(m.stressScore) + ' D' + Math.round(m.depressionScore)
            + ' A' + Math.round(m.anxietyScore) + '</span>';
        btn.addEventListener('click', () => {
            document.querySelectorAll('.viz-search-item').forEach((el) => el.classList.remove('active'));
            btn.classList.add('active');
            if (!OPT_REAL.checked) {
                OPT_REAL.checked = true;
                loadVisualization().then(() => focusOnProfile(m.profileId));
            } else {
                focusOnProfile(m.profileId);
            }
        });
        SEARCH_RESULTS.appendChild(btn);
    });
}

async function searchUsers() {
    const q = (SEARCH_INPUT?.value || '').trim();
    if (!q) {
        SEARCH_RESULTS.innerHTML = '<p class="viz-search-empty">이름을 입력해 주세요.</p>';
        return;
    }
    SEARCH_RESULTS.innerHTML = '<p class="viz-search-empty">검색 중…</p>';
    try {
        const res = await fetch('/api/chat-cluster/search?' + new URLSearchParams({ q }));
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.error || ('HTTP ' + res.status));
        }
        renderSearchResults(await res.json());
    } catch (e) {
        SEARCH_RESULTS.innerHTML = '<p class="viz-search-empty">' + escapeHtml(e.message) + '</p>';
    }
}

// ═════════════════════════════════════════════════════════════════════
// §7. 카메라 · 줌 · 리사이즈 — slider↔OrbitControls 양방향 동기화
// ═════════════════════════════════════════════════════════════════════

function setCameraFromSliders(updateControls = true) {
    const rx = Number(ROT_X.value) * Math.PI / 180;
    const ry = Number(ROT_Y.value) * Math.PI / 180;
    const dist = BASE_DISTANCE / zoom;
    camera.position.set(
        dist * Math.cos(rx) * Math.sin(ry),
        dist * Math.sin(rx),
        dist * Math.cos(rx) * Math.cos(ry),
    );
    camera.lookAt(controls.target);
    if (updateControls) controls.update();
}

function syncSlidersFromCamera() {
    const p = camera.position.clone().sub(controls.target);
    const dist = p.length();
    zoom = clamp(BASE_DISTANCE / dist, ZOOM_MIN, ZOOM_MAX);
    const rx = Math.asin(clamp(p.y / dist, -1, 1)) * 180 / Math.PI;
    let ry = Math.atan2(p.x, p.z) * 180 / Math.PI;
    ROT_X.value = Math.round(rx);
    ROT_Y.value = Math.round(ry);
    updateZoomLabel();
}

function updateZoomLabel() {
    const zl = document.getElementById('zoom-label');
    if (zl) zl.textContent = Math.round(zoom * 100) + '%';
}

function setZoom(z) {
    zoom = clamp(z, ZOOM_MIN, ZOOM_MAX);
    const offset = camera.position.clone().sub(controls.target);
    if (offset.lengthSq() < 1e-6) offset.set(2.6, 2.1, 2.9);
    offset.setLength(BASE_DISTANCE / zoom);
    camera.position.copy(controls.target).add(offset);
    controls.update();
    syncSlidersFromCamera();
}

function onResize() {
    const rect = VIEWPORT.getBoundingClientRect();
    camera.aspect = rect.width / rect.height;
    camera.updateProjectionMatrix();
    renderer.setSize(rect.width, rect.height, false);
}

// ═════════════════════════════════════════════════════════════════════
// §8. 포인터 · 툴팁 — Raycaster 로 hover 처리
// ═════════════════════════════════════════════════════════════════════

function onPointerMove(ev) {
    const rect = CANVAS.getBoundingClientRect();
    pointer.x = ((ev.clientX - rect.left) / rect.width) * 2 - 1;
    pointer.y = -((ev.clientY - rect.top) / rect.height) * 2 + 1;
    raycaster.setFromCamera(pointer, camera);
    const hits = raycaster.intersectObjects(pointsGroup.children, true);
    if (hits.length) {
        let node = hits[0].object;
        while (node.parent && node.parent !== pointsGroup) node = node.parent;
        const entry = pointEntries.find((e) => e.group === node);
        if (!entry) return;

        if (hovered !== entry) hovered = entry;

        pointEntries.forEach((e) => {
            if (focusedProfileId != null && e.p.id === focusedProfileId) return;
            const mul = e === entry ? SIZE_HOVER_MUL : 1;
            applyPointScale(e, mul);
        });
        showTooltip(ev, entry.p);
    } else {
        hovered = null;
        if (focusedProfileId == null) {
            pointEntries.forEach((e) => applyPointScale(e, 1));
        } else {
            applyHighlight();
        }
        hideTooltip();
    }
}

function showTooltip(ev, p) {
    TOOLTIP.style.setProperty('--c', clusterColorVar(p.clusterId));
    const sScore = Math.round(p.stressScore || 0);
    const dScore = Math.round(p.depressionScore || 0);
    const aScore = Math.round(p.anxietyScore || 0);
    const sNorm = (p.stressNorm ?? 0).toFixed(2);
    const dNorm = (p.depressionNorm ?? 0).toFixed(2);
    const aNorm = (p.anxietyNorm ?? 0).toFixed(2);
    const personaKey = p.personaKey || '';
    const cidText = p.clusterId == null ? '—' : p.clusterId;
    const kindText = p.synthetic ? '페르소나 시드' : '실사용자';
    const personaLine = p.synthetic
        ? '<div class="tt-key"><code>' + escapeHtml(personaKey) + '</code></div>'
        : '<div class="tt-key">user_id 비공개</div>';
    TOOLTIP.innerHTML =
        '<div class="tt-head"><span class="swatch"></span><strong>'
        + escapeHtml(p.personaLabel || ('실사용자 #' + (p.id ?? ''))) + '</strong></div>'
        + personaLine
        + '<div class="tt-row"><span>Stress (PSS)</span><b>' + sScore + '</b>/40 · norm ' + sNorm + '</div>'
        + '<div class="tt-row"><span>Depression (PHQ-9)</span><b>' + dScore + '</b>/27 · norm ' + dNorm + '</div>'
        + '<div class="tt-row"><span>Anxiety (GAD-7)</span><b>' + aScore + '</b>/21 · norm ' + aNorm + '</div>'
        + '<div class="tt-foot">cluster_id = <strong>' + cidText + '</strong> · ' + kindText + '</div>';
    TOOLTIP.style.display = 'block';
    moveTooltip(ev);
}

function moveTooltip(ev) {
    const rect = VIEWPORT.getBoundingClientRect();
    TOOLTIP.style.left = (ev.clientX - rect.left + 14) + 'px';
    TOOLTIP.style.top = (ev.clientY - rect.top + 14) + 'px';
}

function hideTooltip() {
    TOOLTIP.style.display = 'none';
}

// ═════════════════════════════════════════════════════════════════════
// §9. 데이터 로드 — visualization / my / recompute + 범례
// ═════════════════════════════════════════════════════════════════════

function buildLegend(centroids) {
    LEGEND.innerHTML = '';
    const labels = ['안정', '스트레스', '우울', '불안', '복합', '중간', '집단 6', '집단 7'];
    centroids.map((c) => c.clusterId).sort((a, b) => a - b).forEach((cid) => {
        const row = document.createElement('div');
        row.className = 'viz-legend-row';
        row.dataset.cluster = cid;
        const swatch = document.createElement('span');
        swatch.className = 'dot';
        swatch.style.background = 'var(--cluster-' + cid + ')';
        const label = document.createElement('span');
        const count = (centroids.find((c) => c.clusterId === cid) || {}).memberCount || 0;
        label.textContent = (labels[cid] || ('집단 ' + cid)) + ' · ' + count + '명';
        row.appendChild(swatch);
        row.appendChild(label);
        row.addEventListener('click', () => {
            highlightCluster = highlightCluster === cid ? null : cid;
            focusedProfileId = null;
            applyHighlight();
            document.querySelectorAll('.viz-legend-row').forEach((r) => r.classList.toggle(
                'muted',
                highlightCluster != null && Number(r.dataset.cluster) !== highlightCluster,
            ));
        });
        LEGEND.appendChild(row);
    });
}

async function loadVisualization() {
    ERR.textContent = '';
    const params = new URLSearchParams({
        includeSynthetic: OPT_SYN.checked,
        includeReal: OPT_REAL.checked,
    });
    try {
        const res = await fetch('/api/chat-cluster/visualization?' + params.toString());
        if (!res.ok) throw new Error('HTTP ' + res.status);
        DATA = await res.json();
        focusedProfileId = null;
        renderPoints(DATA.points);
        renderCentroids(DATA.centroids);
        buildLegend(DATA.centroids);
        applyHighlight();
        const rc = DATA.stats?.realCount ?? 0;
        const sc = DATA.stats?.syntheticCount ?? 0;
        if (OPT_REAL.checked && rc === 0) {
            ERR.textContent = '실사용자 프로필이 0건이에요. sql/CHAT_CLUSTER_REAL_USER_SEED.sql 실행 후 「데이터 새로고침」을 눌러 주세요.';
        } else if (OPT_REAL.checked && !OPT_SYN.checked) {
            ERR.textContent = '실사용자 ' + rc + '명만 표시 중 (페르소나 시드 ' + sc + '명은 숨김)';
        } else {
            ERR.textContent = '';
        }
    } catch (e) {
        ERR.textContent = '데이터를 불러오지 못했어요: ' + e.message;
    }
}

async function loadMyCluster() {
    try {
        const res = await fetch('/api/chat-cluster/my');
        if (res.status === 401) {
            MY.textContent = '로그인하면 내 점·집단이 표시돼요.';
            return;
        }
        const data = await res.json();
        if (data.hasProfile) {
            MY.innerHTML = '<strong>집단 ' + data.clusterId + '</strong> · 같은 위치 '
                + data.clusterMemberCount + '명<br/><span class="muted">'
                + escapeHtml(data.summary || '') + '</span>';
            document.getElementById('btn-recompute').style.display = 'inline-flex';
        } else {
            MY.textContent = data.summary || '아직 내 점수가 저장되지 않았어요.';
        }
    } catch (_) { /* ignore */ }
}

async function recompute() {
    ERR.textContent = '';
    try {
        const res = await fetch('/api/chat-cluster/recompute', { method: 'POST' });
        if (res.status === 401) { ERR.textContent = '로그인이 필요해요.'; return; }
        if (res.status === 403) { ERR.textContent = '관리자만 재계산할 수 있어요.'; return; }
        const data = await res.json();
        ERR.textContent = '재계산 완료 — silhouette=' + (data.silhouette || 0).toFixed(3)
            + ' (' + data.elapsedMillis + 'ms)';
        await loadVisualization();
    } catch (e) {
        ERR.textContent = '재계산 실패: ' + e.message;
    }
}

// ═════════════════════════════════════════════════════════════════════
// §10. UI 이벤트 바인딩 + 부트스트랩
// ═════════════════════════════════════════════════════════════════════

function bindUi() {
    ROT_X.addEventListener('input', () => setCameraFromSliders());
    ROT_Y.addEventListener('input', () => setCameraFromSliders());

    document.getElementById('btn-reset-rot').addEventListener('click', () => {
        ROT_X.value = -22;
        ROT_Y.value = 32;
        zoom = 1;
        setCameraFromSliders();
        updateZoomLabel();
    });

    document.getElementById('btn-zoom-in').addEventListener('click', () => setZoom(zoom + ZOOM_STEP));
    document.getElementById('btn-zoom-out').addEventListener('click', () => setZoom(zoom - ZOOM_STEP));

    document.getElementById('btn-spin').addEventListener('click', (e) => {
        if (spinTimer) {
            clearInterval(spinTimer);
            spinTimer = null;
            e.target.textContent = '자동 회전';
        } else {
            e.target.textContent = '회전 중지';
            spinTimer = setInterval(() => {
                ROT_Y.value = ((Number(ROT_Y.value) + 1.2 + 180) % 360) - 180;
                setCameraFromSliders();
            }, 33);
        }
    });

    OPT_SYN.addEventListener('change', loadVisualization);
    OPT_REAL.addEventListener('change', loadVisualization);
    OPT_CENTROID.addEventListener('change', () => {
        if (DATA) renderCentroids(DATA.centroids);
    });
    document.getElementById('btn-refresh').addEventListener('click', loadVisualization);
    document.getElementById('btn-recompute').addEventListener('click', recompute);

    document.getElementById('btn-user-search').addEventListener('click', searchUsers);
    SEARCH_INPUT?.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            searchUsers();
        }
    });
}

initThree();
bindUi();
updateZoomLabel();
loadVisualization();
loadMyCluster();
