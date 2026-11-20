/* =====================================================================
   FITJANTA shared app logic
   Every page loads this file after Bootstrap.
   ===================================================================== */

const FJ = (() => {

    const USER_KEY = "fitjantaUser";

    // ------------------------------------------------------------------
    // Session
    // ------------------------------------------------------------------

    function user() {
        try {
            return JSON.parse(localStorage.getItem(USER_KEY));
        } catch (e) {
            return null;
        }
    }

    function setUser(u) {
        if (u) localStorage.setItem(USER_KEY, JSON.stringify(u));
    }

    function logout() {
        localStorage.removeItem(USER_KEY);
        window.location.href = "login.html";
    }

    function requireUser() {
        const u = user();
        if (!u || !u.id) {
            window.location.replace("login.html");
            throw new Error("Not logged in");
        }
        return u;
    }

    async function refreshUser() {
        const u = user();
        if (!u) return null;
        try {
            const fresh = await api("/api/users/" + u.id);
            setUser(fresh);
            return fresh;
        } catch (e) {
            if (e.status === 404) logout();
            return u;
        }
    }

    // ------------------------------------------------------------------
    // API helper: JSON in, JSON out, readable error messages
    // ------------------------------------------------------------------

    async function api(path, options = {}) {
        const init = { method: options.method || "GET", headers: {} };
        if (options.form) {
            init.body = options.form;
        } else if (options.body !== undefined) {
            init.headers["Content-Type"] = "application/json";
            init.body = JSON.stringify(options.body);
        }

        let response;
        try {
            response = await fetch(path, init);
        } catch (e) {
            const err = new Error("Can't reach the FitJanta server. Check your Wi-Fi.");
            err.status = 0;
            throw err;
        }

        const text = await response.text();
        let data = null;
        try { data = text ? JSON.parse(text) : null; } catch (e) { data = text; }

        if (!response.ok) {
            const message = (data && data.message) || (typeof data === "string" && data) || "Something went wrong.";
            const err = new Error(message);
            err.status = response.status;
            throw err;
        }
        return data;
    }

    // ------------------------------------------------------------------
    // UI helpers
    // ------------------------------------------------------------------

    function esc(value) {
        return String(value ?? "").replace(/[&<>"']/g, c => ({
            "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
        }[c]));
    }

    function initials(name) {
        const parts = String(name || "?").trim().split(/\s+/);
        return ((parts[0] || "?")[0] + (parts.length > 1 ? parts[parts.length - 1][0] : "")).toUpperCase();
    }

    const AVATAR_GRADIENTS = [
        "linear-gradient(135deg,#FF8A2B,#FF2E7E)",
        "linear-gradient(135deg,#8B5CF6,#38D9F5)",
        "linear-gradient(135deg,#22C55E,#C5F82A)",
        "linear-gradient(135deg,#F59E0B,#EF4444)",
        "linear-gradient(135deg,#06B6D4,#3B82F6)",
        "linear-gradient(135deg,#EC4899,#8B5CF6)"
    ];

    // Avatar: profile photo if the user has one, otherwise coloured initials
    function avatar(name, size = 40, ring = false, url = null) {
        let hash = 0;
        for (const ch of String(name || "")) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
        const bg = AVATAR_GRADIENTS[hash % AVATAR_GRADIENTS.length];
        const el = url
            ? `<img class="avatar avatar-img" src="${esc(url)}" alt="${esc(name)}" style="--s:${size}px;background:${bg}" loading="lazy">`
            : `<span class="avatar" style="--s:${size}px;background:${bg}">${esc(initials(name))}</span>`;
        return ring ? `<span class="avatar-ring">${el}</span>` : el;
    }

    // Avatar for a user object ({ name, avatarUrl })
    function avatarOf(u, size = 40, ring = false) {
        return avatar(u && u.name, size, ring, u && (u.avatarUrl || u.userAvatar));
    }

    // Link to a user's profile (your own profile page for yourself)
    function profileHref(userId) {
        const me = user();
        return me && me.id === userId ? "profile.html" : "user.html?id=" + userId;
    }

    function colorFor(id) {
        const palette = ["#FF5A3C", "#8B5CF6", "#22C55E", "#38D9F5", "#FFC23D", "#EC4899", "#3B82F6", "#F97316"];
        return palette[Math.abs(Number(id) || 0) % palette.length];
    }

    function distanceKm(lat1, lon1, lat2, lon2) {
        const R = 6371, toRad = d => d * Math.PI / 180;
        const dLat = toRad(lat2 - lat1), dLon = toRad(lon2 - lon1);
        const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    function fmtDistance(km) {
        if (km == null) return "";
        return km < 1 ? Math.round(km * 1000) + " m" : (Math.round(km * 10) / 10) + " km";
    }

    function timeAgo(value) {
        if (!value) return "";
        const date = new Date(value);
        const s = Math.floor((Date.now() - date.getTime()) / 1000);
        if (s < 45) return "just now";
        if (s < 3600) return Math.floor(s / 60) + "m ago";
        if (s < 86400) return Math.floor(s / 3600) + "h ago";
        if (s < 604800) return Math.floor(s / 86400) + "d ago";
        return date.toLocaleDateString(undefined, { day: "numeric", month: "short" });
    }

    function greeting() {
        const h = new Date().getHours();
        if (h < 5) return "Good night";
        if (h < 12) return "Good morning";
        if (h < 17) return "Good afternoon";
        return "Good evening";
    }

    function fmt(n) {
        return Number(n || 0).toLocaleString("en-IN");
    }

    function brand(size = "") {
        return `<a href="home.html" class="brand ${size}">
                    <img src="img/logo-mark.svg" alt="">
                    <span class="wordmark">FIT<span>JANTA</span></span>
                </a>`;
    }

    // Bottom tab bar
    function nav(active) {
        const items = [
            ["home", "home.html", "bi-house-door-fill", "Home"],
            ["food", "food.html", "bi-egg-fried", "Food AI"],
            ["post", "new-post.html", "bi-plus-lg", ""],
            ["feed", "feed.html", "bi-images", "Feed"],
            ["profile", "profile.html", "bi-person-fill", "Profile"]
        ];
        const html = items.map(([key, href, icon, label]) => key === "post"
            ? `<a href="${href}" class="nav-plus" aria-label="New post"><span><i class="bi ${icon}"></i></span></a>`
            : `<a href="${href}" class="${key === active ? "active" : ""}"><i class="bi ${icon}"></i>${label}</a>`
        ).join("");
        const el = document.createElement("nav");
        el.className = "bottom-nav";
        el.innerHTML = html;
        document.body.appendChild(el);
    }

    function toast(message, type = "info") {
        let box = document.getElementById("fjToasts");
        if (!box) {
            box = document.createElement("div");
            box.id = "fjToasts";
            box.className = "toast-container position-fixed top-0 start-50 translate-middle-x p-3";
            box.style.zIndex = 2100;
            document.body.appendChild(box);
        }
        const icons = { success: "bi-check-circle-fill text-lime", error: "bi-exclamation-circle-fill text-pink", info: "bi-stars text-orange" };
        const el = document.createElement("div");
        el.className = "toast fj-toast align-items-center";
        el.setAttribute("role", "status");
        el.innerHTML = `<div class="d-flex align-items-center gap-2 px-3 py-2">
                <i class="bi ${icons[type] || icons.info} fs-5"></i>
                <div class="flex-grow-1 small fw-semibold">${esc(message)}</div>
            </div>`;
        box.appendChild(el);
        const t = bootstrap.Toast.getOrCreateInstance(el, { delay: 2800 });
        el.addEventListener("hidden.bs.toast", () => el.remove());
        t.show();
    }

    function haptic() {
        if (navigator.vibrate) navigator.vibrate(12);
    }

    // Animated number counter
    function countUp(el, to, duration = 900) {
        if (!el) return;
        const from = 0;
        const start = performance.now();
        const step = now => {
            const p = Math.min(1, (now - start) / duration);
            const eased = 1 - Math.pow(1 - p, 3);
            el.textContent = fmt(Math.round(from + (to - from) * eased));
            if (p < 1) requestAnimationFrame(step);
        };
        requestAnimationFrame(step);
    }

    // Full-screen XP celebration with confetti
    function celebrate(title, subtitle = "") {
        haptic();
        const overlay = document.createElement("div");
        overlay.className = "celebrate";
        overlay.innerHTML = `<div class="burst px-4">
                <div class="xp text-grad">${esc(title)}</div>
                <div class="fw-semibold mt-1">${esc(subtitle)}</div>
                <div class="small text-muted mt-3">Tap to continue</div>
            </div>`;
        document.body.appendChild(overlay);

        const colors = ["#FF8A2B", "#FF2E7E", "#C5F82A", "#38D9F5", "#8B5CF6", "#FFC23D"];
        for (let i = 0; i < 60; i++) {
            const c = document.createElement("span");
            c.className = "confetti";
            c.style.left = Math.random() * 100 + "vw";
            c.style.background = colors[i % colors.length];
            c.style.animationDuration = 1.6 + Math.random() * 1.6 + "s";
            c.style.animationDelay = Math.random() * .3 + "s";
            c.style.transform = `rotate(${Math.random() * 360}deg)`;
            document.body.appendChild(c);
            setTimeout(() => c.remove(), 3600);
        }
        const close = () => overlay.remove();
        overlay.addEventListener("click", close);
        setTimeout(close, 2600);
    }

    function setLoading(button, loading, text) {
        if (!button) return;
        if (loading) {
            button.dataset.label = button.innerHTML;
            button.disabled = true;
            button.innerHTML = `<span class="spinner-border spinner-border-sm me-2"></span>${esc(text || "Please wait")}`;
        } else {
            button.disabled = false;
            if (button.dataset.label) button.innerHTML = button.dataset.label;
        }
    }

    // Resize a photo in the browser before upload (fast uploads over Wi-Fi)
    async function compressImage(file, maxSide = 1440, quality = 0.86) {
        const url = URL.createObjectURL(file);
        try {
            const img = await new Promise((resolve, reject) => {
                const i = new Image();
                i.onload = () => resolve(i);
                i.onerror = () => reject(new Error("This photo format isn't supported. Try a JPG or PNG."));
                i.src = url;
            });
            const scale = Math.min(1, maxSide / Math.max(img.naturalWidth, img.naturalHeight));
            const w = Math.round(img.naturalWidth * scale);
            const h = Math.round(img.naturalHeight * scale);
            const canvas = document.createElement("canvas");
            canvas.width = w;
            canvas.height = h;
            canvas.getContext("2d").drawImage(img, 0, 0, w, h);
            return await new Promise(resolve => canvas.toBlob(resolve, "image/jpeg", quality));
        } finally {
            URL.revokeObjectURL(url);
        }
    }

    // Turns #hashtags into highlighted text (safe: input is escaped first)
    function formatCaption(text) {
        return esc(text).replace(/(^|\s)(#[\p{L}\p{N}_]+)/gu, '$1<span class="hashtag">$2</span>');
    }

    const ACTIVITY_META = {
        Walking: { icon: "bi-person-walking", tint: "tint-lime" },
        Running: { icon: "bi-lightning-charge-fill", tint: "tint-orange" },
        Cycling: { icon: "bi-bicycle", tint: "tint-cyan" },
        Swimming: { icon: "bi-water", tint: "tint-cyan" },
        Workout: { icon: "bi-heart-pulse-fill", tint: "tint-pink" },
        Yoga: { icon: "bi-flower1", tint: "tint-violet" },
        Nutrition: { icon: "bi-egg-fried", tint: "tint-amber" }
    };

    function activityMeta(type) {
        return ACTIVITY_META[type] || { icon: "bi-activity", tint: "tint-orange" };
    }

    return {
        user, setUser, logout, requireUser, refreshUser, api,
        esc, initials, avatar, avatarOf, profileHref, colorFor, distanceKm, fmtDistance, timeAgo, greeting, fmt, brand, nav, toast, haptic,
        countUp, celebrate, setLoading, compressImage, formatCaption, activityMeta
    };
})();
