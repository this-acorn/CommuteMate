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
    var title = form.querySelector("[data-auth-title]");
    var subtitle = form.querySelector("[data-auth-subtitle]");
    var submitBtn = form.querySelector("[data-auth-submit]");

    function setMode(mode) {
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
      if (title) title.textContent = signup ? "Create your account" : "Welcome back";
      if (subtitle) {
        subtitle.textContent = signup
          ? "Verified SFU students only. Takes about a minute."
          : "Log in to see today's rides up the mountain.";
      }
      if (submitBtn) submitBtn.textContent = signup ? "Create account" : "Log in";
    }

    tabs.forEach(function (t) {
      t.addEventListener("click", function () { setMode(t.getAttribute("data-mode")); });
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
    var cards = Array.prototype.slice.call(grid.querySelectorAll("[data-ride]"));

    // Initial sort = the chip the server rendered as active (bg-primary)
    var currentSort = "Departure";
    chips.forEach(function (c) {
      if (c.classList.contains("bg-primary")) currentSort = c.getAttribute("data-sort");
    });

    var sorters = {
      "Departure": function (a, b) { return str(a, "depart").localeCompare(str(b, "depart")); },
      "Price": function (a, b) { return num(a, "price") - num(b, "price"); },
      "Eco-Score": function (a, b) { return num(b, "eco") - num(a, "eco"); },
      "Rating": function (a, b) { return num(b, "rating") - num(a, "rating"); }
    };

    function num(card, key) { return parseFloat(card.getAttribute("data-" + key)) || 0; }
    function str(card, key) { return card.getAttribute("data-" + key) || ""; }

    function apply() {
      var q = (search ? search.value : "").trim().toLowerCase();
      var visible = 0;

      var ordered = cards.slice().sort(sorters[currentSort] || sorters.Departure);
      ordered.forEach(function (card) {
        var hay = (card.getAttribute("data-search") || "").toLowerCase();
        var match = hay.indexOf(q) !== -1;
        card.style.display = match ? "" : "none";
        if (match) visible++;
        grid.appendChild(card); // re-order in DOM
      });

      if (count) count.textContent = visible;
      if (empty) empty.style.display = visible === 0 ? "" : "none";
    }

    if (search) search.addEventListener("input", apply);
    chips.forEach(function (chip) {
      chip.addEventListener("click", function (e) {
        e.preventDefault(); // sort in place instead of reloading via the form
        chips.forEach(function (c) { swapClasses(c, CHIP_ACTIVE, CHIP_INACTIVE); });
        swapClasses(chip, CHIP_INACTIVE, CHIP_ACTIVE);
        currentSort = chip.getAttribute("data-sort");
        apply();
      });
    });

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
