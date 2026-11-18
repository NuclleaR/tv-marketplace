(() => {
  const strings = {
    en: {
      title: "Send to TV",
      pinHint: "Enter the code shown on your TV.",
      continue: "Continue",
      linkLabel: "GitHub link",
      paste: "Paste",
      send: "Send",
      expired: "This code has expired. Scan the new one on your TV.",
      sending: "Sending…",
      sent: "Sent. Confirm it on your TV.",
      duplicate: "Sent. This app is already on your TV.",
      unsupported: "This link isn't supported yet. Send a GitHub link, for example:",
      unreachable: "Can't reach your TV. Is your phone on the same Wi-Fi as the TV?",
      wrongPin: (left) => `Wrong code. Attempts left: ${left}.`,
      noSession: "Open “Add app” on your TV first.",
      pasteHint: "Long-press the field and choose Paste.",
      failed: "Something went wrong. Try again.",
      remember: "Remember this TV, so next time you can send without scanning",
      saved: "Sent. This TV is remembered: add this page to your home screen to send links later.",
      revoked: "This TV forgot your phone. Open “Add app” on the TV and scan the code again.",
      tvClosed: "Can't reach your TV. Open TV Marketplace on the TV and make sure your phone is on the same Wi-Fi.",
      shortcut: "Add “Send to TV” to the iPhone Share menu",
    },
    uk: {
      title: "Надіслати на ТВ",
      pinHint: "Введіть код, який показує телевізор.",
      continue: "Далі",
      linkLabel: "Посилання на GitHub",
      paste: "Вставити",
      send: "Надіслати",
      expired: "Код застарів. Відскануйте новий на телевізорі.",
      sending: "Надсилаємо…",
      sent: "Надіслано. Підтвердіть на телевізорі.",
      duplicate: "Надіслано. Цей застосунок уже є на телевізорі.",
      unsupported: "Це посилання поки не підтримується. Надішліть посилання на GitHub, наприклад:",
      unreachable: "Не вдається зʼєднатися з телевізором. Телефон у тій самій Wi-Fi, що й ТВ?",
      wrongPin: (left) => `Неправильний код. Залишилось спроб: ${left}.`,
      noSession: "Спершу відкрийте «Додати застосунок» на телевізорі.",
      pasteHint: "Утримуйте палець на полі й виберіть «Вставити».",
      failed: "Щось пішло не так. Спробуйте ще раз.",
      remember: "Запамʼятати цей телевізор, щоб наступного разу надсилати без сканування",
      saved: "Надіслано. Телевізор запамʼятано: додайте цю сторінку на головний екран, щоб надсилати посилання пізніше.",
      revoked: "Телевізор забув ваш телефон. Відкрийте «Додати застосунок» на ТВ і відскануйте код ще раз.",
      tvClosed: "Не вдається зʼєднатися з телевізором. Відкрийте TV Marketplace на ТВ і перевірте, що телефон у тій самій Wi-Fi.",
      shortcut: "Додати «Надіслати на ТВ» у меню «Поділитися» на iPhone",
    },
  };
  const lang = (navigator.language || "en").toLowerCase().startsWith("uk") ? "uk" : "en";
  const t = strings[lang];
  document.documentElement.lang = lang;
  document.querySelectorAll("[data-i18n]").forEach((el) => { el.textContent = t[el.dataset.i18n]; });
  document.title = t.title;

  const $ = (id) => document.getElementById(id);
  const status = $("status");
  const views = ["pin-view", "send-view", "expired-view", "revoked-view"];
  const show = (id) => views.forEach((v) => { $(v).hidden = v !== id; });
  const say = (text, kind) => { status.textContent = text; status.className = kind || ""; };

  const state = document.querySelector('meta[name="session-state"]').content;
  const pathToken = location.pathname.startsWith("/s/") ? decodeURIComponent(location.pathname.slice(3)) : null;
  let sessionToken = state === "valid" ? pathToken : null;

  // storage can be unavailable (private mode); the page then works like a one-off send
  const DEVICE_KEY = "tvMarketplaceDeviceToken";
  const storage = {
    get() { try { return localStorage.getItem(DEVICE_KEY); } catch (_) { return null; } },
    set(value) { try { localStorage.setItem(DEVICE_KEY, value); } catch (_) { /* not remembered */ } },
    clear() { try { localStorage.removeItem(DEVICE_KEY); } catch (_) { /* nothing stored */ } },
  };
  let deviceToken = storage.get();

  async function api(path, body) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 5000);
    const headers = {};
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (deviceToken) headers["Authorization"] = `Bearer ${deviceToken}`;
    try {
      const response = await fetch(path, {
        method: body === undefined ? "GET" : "POST",
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: controller.signal,
      });
      let json = {};
      try { json = await response.json(); } catch (_) { /* empty body */ }
      return { status: response.status, json };
    } finally {
      clearTimeout(timer);
    }
  }

  function revoked() {
    deviceToken = null;
    storage.clear();
    show("revoked-view");
    say("");
  }

  function showSend() {
    $("remember-row").hidden = !!deviceToken;
    show("send-view");
    // the iOS Shortcut opens /send?u=<link>: prefill only, sending stays a tap of the user
    const shared = new URLSearchParams(location.search).get("u");
    if (shared && !$("link").value) $("link").value = shared;
    $("link").focus();
  }

  async function offerShortcut() {
    if (!/iPhone|iPad/.test(navigator.userAgent)) return;
    try {
      const config = await (await fetch("/assets/config.json")).json();
      if (config.iosShortcutUrl) {
        $("shortcut").href = config.iosShortcutUrl;
        $("shortcut-row").hidden = false;
      }
    } catch (_) { /* no shortcut published */ }
  }

  async function start() {
    if (deviceToken && !sessionToken) {
      showSend();
      try {
        const { status: code } = await api("/api/v1/ping");
        if (code === 403) revoked();
      } catch (_) {
        say(t.tvClosed, "error");
      }
      return;
    }
    if (state === "expired") return show("expired-view");
    if (sessionToken) {
      showSend();
    } else {
      show("pin-view");
      $("pin").focus();
    }
  }

  $("pin-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    say("");
    try {
      const { status: code, json } = await api("/api/v1/sessions/pin", { pin: $("pin").value.trim() });
      if (code === 200) {
        sessionToken = json.sessionToken;
        showSend();
      } else if (code === 401) {
        say(t.wrongPin(json.attemptsLeft ?? 0), "error");
      } else if (code === 410) {
        say(t.noSession, "error");
      } else {
        say(t.failed, "error");
      }
    } catch (_) {
      say(t.unreachable, "error");
    }
  });

  $("paste").addEventListener("click", async () => {
    // reading the clipboard needs HTTPS, which a TV on the home network can't offer; it's tried anyway
    try {
      if (navigator.clipboard && navigator.clipboard.readText) {
        $("link").value = await navigator.clipboard.readText();
        return;
      }
    } catch (_) { /* fall through to the hint */ }
    $("link").focus();
    say(t.pasteHint);
  });

  $("send-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const text = $("link").value.trim();
    if (!text) return;
    $("send").disabled = true;
    say(t.sending);
    try {
      const pair = !deviceToken && $("remember").checked;
      const { status: code, json } = await api("/api/v1/links", { text, sessionToken, pair });
      if (code === 202) {
        if (json.deviceToken) {
          deviceToken = json.deviceToken;
          storage.set(deviceToken);
          // the saved page has no session in its address: open the plain one, so a bookmark keeps working
          history.replaceState(null, "", "/");
          $("remember-row").hidden = true;
          say(t.saved, "ok");
        } else {
          say(json.duplicate ? t.duplicate : t.sent, "ok");
        }
        $("link").value = "";
      } else if (code === 422) {
        status.className = "error";
        status.textContent = t.unsupported + " ";
        const example = document.createElement("code");
        example.textContent = (json.examples || [])[0] || "https://github.com/owner/repo";
        status.appendChild(example);
      } else if (code === 403) {
        revoked();
      } else if (code === 401) {
        sessionToken = null;
        show("expired-view");
        say("");
      } else {
        say(t.failed, "error");
      }
    } catch (_) {
      say(deviceToken ? t.tvClosed : t.unreachable, "error");
    } finally {
      $("send").disabled = false;
    }
  });

  void start();
  void offerShortcut();
})();
