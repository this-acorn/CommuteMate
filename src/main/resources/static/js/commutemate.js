/* =========================================================================
   CommuteMate — page interactions
   One file, loaded on every page. Each block no-ops unless its markup exists,
   so behaviour is progressive: the server-rendered pages work without JS.
   Class names toggled here match the Lovable prototype's Tailwind classes.
   ========================================================================= */
(function () {
  "use strict";

  document.addEventListener("DOMContentLoaded", function () {
    initAuthToggle();
    initAvailableRides();
    initCreateRide();
  });

  function swapClasses(el, remove, add) {
    remove.forEach(function (c) { el.classList.remove(c); });
    add.forEach(function (c) { el.classList.add(c); });
  }

  /* ---------------------------------------------------------- auth page */
  var TAB_ACTIVE = ["bg-background", "text-foreground", "shadow-sm"];
  var TAB_INACTIVE = ["text-muted-foreground"];
  var ROLE_ACTIVE = ["border-primary", "bg-primary/10", "text-primary"];
  var ROLE_INACTIVE = ["border-border", "bg-background", "hover:bg-secondary"];

  function initAuthToggle() {
    var form = document.querySelector("[data-auth-form]");
    if (!form) return;

    var modeInput = form.querySelector("input[name='mode']");
    var tabs = form.querySelectorAll("[data-mode]");
    var signupOnly = form.querySelectorAll("[data-signup-only]");
    var loginOnly = form.querySelectorAll("[data-login-only]");
    var title = form.querySelector("[data-auth-title]");
    var subtitle = form.querySelector("[data-auth-subtitle]");
    var submitBtn = form.querySelector("[data-auth-submit]");

    function setMode(mode, isUserAction) {
      modeInput.value = mode;
      // Login submits to Spring Security's form login; sign-up to /register
      form.setAttribute("action", mode === "signup" ? "/register" : "/login");
      tabs.forEach(function (t) {
        if (t.getAttribute("data-mode") === mode) {
          swapClasses(t, TAB_INACTIVE, TAB_ACTIVE);
        } else {
          swapClasses(t, TAB_ACTIVE, TAB_INACTIVE);
        }
      });
      var signup = mode === "signup";
      signupOnly.forEach(function (el) { el.style.display = signup ? "" : "none"; });
      // "Keep me signed in" only means anything on the login form
      loginOnly.forEach(function (el) { el.style.display = signup ? "none" : ""; });
      if (title) title.textContent = signup ? "Create your account" : "Welcome back";
      if (subtitle) {
        subtitle.textContent = signup
          ? "Verified SFU students only. Takes about a minute."
          : "Log in to see today's rides up the mountain.";
      }
      if (submitBtn) submitBtn.textContent = signup ? "Create account" : "Log in";

      // Server-rendered error/success banners belong to the mode the page
      // loaded with (e.g. a failed /login redirect); a manual tab switch
      // leaves that state behind, so clear them instead of carrying them over.
      if (isUserAction) {
        form.querySelectorAll("[data-auth-alert]").forEach(function (el) {
          el.style.display = "none";
        });
      }
    }

    tabs.forEach(function (t) {
      t.addEventListener("click", function () { setMode(t.getAttribute("data-mode"), true); });
    });

    // Role selector (sign-up only)
    var roleInput = form.querySelector("input[name='role']");
    var roleButtons = form.querySelectorAll("[data-role]");
    roleButtons.forEach(function (b) {
      b.addEventListener("click", function () {
        roleButtons.forEach(function (x) { swapClasses(x, ROLE_ACTIVE, ROLE_INACTIVE); });
        swapClasses(b, ROLE_INACTIVE, ROLE_ACTIVE);
        if (roleInput) roleInput.value = b.getAttribute("data-role");
      });
    });

    setMode(modeInput.value || "login");
  }

  /* ----------------------------------------------------- available rides */
  var CHIP_ACTIVE = ["bg-primary", "text-primary-foreground"];
  var CHIP_INACTIVE = ["bg-secondary", "hover:bg-secondary/70"];

  function initAvailableRides() {
    var grid = document.querySelector("[data-rides-grid]");
    if (!grid) return;

    var search = document.querySelector("[data-rides-search]");
    var chips = document.querySelectorAll("[data-sort]");
    var count = document.querySelector("[data-rides-count]");
    var empty = document.querySelector("[data-rides-empty]");
    var applyBtn = document.querySelector("[data-rides-apply]");
    var form = document.querySelector("[data-rides-filters]");
    var departure = form ? form.querySelector("input[name='departure']") : null;
    var destination = form ? form.querySelector("input[name='destination']") : null;
    var cards = Array.prototype.slice.call(grid.querySelectorAll("[data-ride]"));

    var fields = { q: search, departure: departure, destination: destination };

    // The server filters on these too, so arriving with them in the URL renders a
    // partial list that client-side filtering can never widen again. Reload once
    // to the bare URL for the full set, carrying the values in the hash where the
    // server cannot act on them, then restore them into the boxes below.
    var params = new URLSearchParams(window.location.search);
    if (Object.keys(fields).some(function (k) { return params.get(k); })) {
      var carried = new URLSearchParams();
      Object.keys(fields).forEach(function (k) {
        if (params.get(k)) carried.set(k, params.get(k));
        params.delete(k);
      });
      var rest = params.toString();
      window.location.replace(window.location.pathname
        + (rest ? "?" + rest : "") + "#" + carried.toString());
      return;
    }

    if (window.location.hash.length > 1) {
      var restored = new URLSearchParams(window.location.hash.slice(1));
      Object.keys(fields).forEach(function (k) {
        if (fields[k] && restored.get(k)) fields[k].value = restored.get(k);
      });
    }

    // Initial sort = the chip the server rendered as active (bg-primary)
    var currentSort = "Departure";
    chips.forEach(function (c) {
      if (c.classList.contains("bg-primary")) currentSort = c.getAttribute("data-sort");
    });

    function setSort(value) {
      currentSort = value;
      chips.forEach(function (c) {
        var active = c.getAttribute("data-sort") === value;
        swapClasses(c, active ? CHIP_INACTIVE : CHIP_ACTIVE, active ? CHIP_ACTIVE : CHIP_INACTIVE);
      });
    }

    var sorters = {
      "Departure": function (a, b) { return str(a, "depart").localeCompare(str(b, "depart")); },
      "Price": function (a, b) { return num(a, "price") - num(b, "price"); },
      "Eco-Score": function (a, b) { return num(b, "eco") - num(a, "eco"); },
      "Rating": function (a, b) { return num(b, "rating") - num(a, "rating"); }
    };

    function num(card, key) { return parseFloat(card.getAttribute("data-" + key)) || 0; }
    function str(card, key) { return card.getAttribute("data-" + key) || ""; }

    function val(input) { return (input ? input.value : "").trim().toLowerCase(); }

    function apply() {
      var q = val(search);
      var from = val(departure);
      var to = val(destination);
      var visible = 0;

      var ordered = cards.slice().sort(sorters[currentSort] || sorters.Departure);
      ordered.forEach(function (card) {
        var match = str(card, "search").indexOf(q) !== -1
          && str(card, "from").indexOf(from) !== -1
          && str(card, "to").indexOf(to) !== -1;
        card.style.display = match ? "" : "none";
        if (match) visible++;
        grid.appendChild(card); // re-order in DOM
      });

      if (count) count.textContent = visible;
      if (empty) empty.style.display = visible === 0 ? "" : "none";
    }

    // Keep the hash on the boxes so a reload or a shared link reproduces what is
    // on screen. Emptied boxes drop out entirely, so clearing every filter gets
    // the bare URL back instead of resurrecting the values on the next load.
    function syncHash() {
      var current = new URLSearchParams();
      Object.keys(fields).forEach(function (k) {
        var v = fields[k] ? fields[k].value.trim() : "";
        if (v) current.set(k, v);
      });
      var hash = current.toString();
      history.replaceState(null, "", window.location.pathname
        + window.location.search + (hash ? "#" + hash : ""));
    }

    // All three boxes filter live, so the Apply button has nothing left to do and
    // submitting would only cost a round trip.
    Object.keys(fields).forEach(function (k) {
      if (fields[k]) fields[k].addEventListener("input", function () {
        apply();
        syncHash();
      });
    });
    if (applyBtn) applyBtn.style.display = "none";
    if (form) form.addEventListener("submit", function (e) { e.preventDefault(); });

    chips.forEach(function (chip) {
      // Sorting is client-side now, so they are not submit buttons any more.
      chip.type = "button";
      chip.addEventListener("click", function () {
        setSort(chip.getAttribute("data-sort"));
        apply();
      });
    });

    setSort(currentSort);
    apply();
  }

  /* --------------------------------------------------------- create ride */
  function initCreateRide() {
    var form = document.querySelector("[data-create-form]");
    if (!form) return;

    var els = {
      from: form.querySelector("[name='from']"),
      to: form.querySelector("[name='to']"),
      date: form.querySelector("[name='date']"),
      time: form.querySelector("[name='time']"),
      seats: form.querySelector("[name='seats']"),
      price: form.querySelector("[name='price']")
    };

    var out = {
      route: document.querySelector("[data-preview-route]"),
      when: document.querySelector("[data-preview-when]"),
      seats: document.querySelector("[data-preview-seats]"),
      seatsLabel: document.querySelector("[data-seats-label]"),
      priceLabel: document.querySelector("[data-price-label]"),
      points: document.querySelector("[data-est-points]"),
      eco: document.querySelector("[data-est-eco]"),
      cost: document.querySelector("[data-est-cost]")
    };

    function update() {
      var seats = parseInt(els.seats.value, 10) || 0;
      var price = parseInt(els.price.value, 10) || 0;

      if (out.route) out.route.textContent = els.from.value + " → " + els.to.value;
      if (out.when) out.when.textContent = els.date.value + " at " + els.time.value;
      if (out.seats) out.seats.textContent = seats + (seats === 1 ? " seat" : " seats");
      if (out.seatsLabel) out.seatsLabel.textContent = seats;
      if (out.priceLabel) out.priceLabel.textContent = "$" + price;

      if (out.points) out.points.textContent = "+" + (seats * 8 + 5);
      if (out.eco) out.eco.textContent = Math.min(95, 55 + seats * 8);
      if (out.cost) out.cost.textContent = "$" + price * seats;
    }

    Object.keys(els).forEach(function (k) {
      if (!els[k]) return;
      els[k].addEventListener("input", update);
      els[k].addEventListener("change", update);
    });
    update();
  }
})();
