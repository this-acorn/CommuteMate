document.addEventListener("DOMContentLoaded", function () {
  var thread = document.getElementById("chat-thread");
  var messageList = document.getElementById("message-list");
  var emptyChat = document.getElementById("empty-chat");
  var form = document.getElementById("chat-form");
  var message = document.getElementById("message");
  var sendButton = document.getElementById("send-button");
  var errorBox = document.getElementById("chat-error");
  var polling = false;
  var sending = false;
  var pollingStopped = false;
  var pollAgainWithScroll = false;
  var pollingTimer = null;

  if (!thread || !messageList || !form || !message) {
    return;
  }

  var existingMessages = Array.from(
    messageList.querySelectorAll("[data-message-id]")
  );
  var lastMessageId = existingMessages.reduce(function (maximum, element) {
    var id = Number(element.dataset.messageId);
    return Number.isFinite(id) ? Math.max(maximum, id) : maximum;
  }, 0);

  function resizeMessageBox() {
    message.style.height = "auto";
    message.style.height = Math.min(message.scrollHeight, 128) + "px";
  }

  function isNearBottom() {
    return thread.scrollHeight - thread.scrollTop - thread.clientHeight < 80;
  }

  function scrollToBottom() {
    requestAnimationFrame(function () {
      thread.scrollTop = thread.scrollHeight;
    });
  }

  function showError(text) {
    if (!errorBox) {
      return;
    }
    errorBox.textContent = text;
    errorBox.hidden = false;
  }

  function clearError() {
    if (errorBox) {
      errorBox.hidden = true;
      errorBox.textContent = "";
    }
  }

  function stopPolling() {
    pollingStopped = true;
    if (pollingTimer !== null) {
      window.clearInterval(pollingTimer);
      pollingTimer = null;
    }
  }

  function wasRedirectedToLogin(response) {
    return response.redirected
      || (response.url && response.url.includes("/auth"));
  }

  function isJsonResponse(response) {
    var contentType = response.headers.get("content-type") || "";
    return contentType.toLowerCase().includes("application/json");
  }

  function handleAuthenticationFailure(response) {
    if (wasRedirectedToLogin(response) || response.status === 401) {
      showError("Your session expired. Sign in again before sending another message.");
      stopPolling();
      return true;
    }
    if (response.status === 403) {
      showError("You no longer have access to this chat.");
      stopPolling();
      return true;
    }
    return false;
  }

  function appendMessage(item) {
    if (!item || item.id == null || Number(item.id) <= lastMessageId) {
      return;
    }

    if (emptyChat) {
      emptyChat.hidden = true;
    }

    var article = document.createElement("article");
    article.dataset.messageId = item.id;
    article.className = "chat-message rounded-xl border border-border bg-card px-4 py-3";
    if (item.mine) {
      article.className += " chat-message-mine border-primary/30 bg-primary/5";
    }

    var header = document.createElement("div");
    header.className = "flex items-start justify-between gap-3";

    var sender = document.createElement("div");
    var senderName = document.createElement("p");
    senderName.className = "text-sm font-semibold";
    senderName.textContent = item.mine ? "You" : item.senderName;
    var senderEmail = document.createElement("p");
    senderEmail.className = "text-xs text-muted-foreground";
    senderEmail.textContent = item.senderEmail;
    sender.append(senderName, senderEmail);

    var sentAt = document.createElement("time");
    sentAt.className = "text-xs text-muted-foreground";
    sentAt.textContent = item.sentAt;
    header.append(sender, sentAt);

    var body = document.createElement("p");
    body.className = "chat-message-body mt-2 text-sm";
    body.textContent = item.body;

    article.append(header, body);
    messageList.appendChild(article);
    lastMessageId = Math.max(lastMessageId, Number(item.id));
  }

  async function loadNewMessages(forceScroll) {
    if (pollingStopped) {
      return;
    }
    if (polling) {
      pollAgainWithScroll = pollAgainWithScroll || Boolean(forceScroll);
      return;
    }

    polling = true;
    var shouldScroll = Boolean(forceScroll) || isNearBottom();

    try {
      var separator = form.dataset.pollUrl.includes("?") ? "&" : "?";
      var response = await fetch(
        form.dataset.pollUrl + separator + "after=" + encodeURIComponent(lastMessageId),
        {
          credentials: "same-origin",
          headers: {"Accept": "application/json"}
        }
      );

      if (handleAuthenticationFailure(response)) {
        return;
      }
      if (!response.ok) {
        if (response.status === 404) {
          showError(await response.text() || "This ride could not be found.");
          stopPolling();
        }
        return;
      }
      if (!isJsonResponse(response)) {
        showError("The chat session is no longer valid. Refresh the page and sign in again.");
        stopPolling();
        return;
      }

      var newMessages = await response.json();
      if (!Array.isArray(newMessages)) {
        showError("The chat returned an invalid response. Please refresh the page.");
        stopPolling();
        return;
      }

      newMessages.forEach(appendMessage);
      if (newMessages.length && shouldScroll) {
        scrollToBottom();
      }
    } catch (error) {
      // A temporary network failure should not interrupt typing or erase the draft.
    } finally {
      polling = false;
      if (pollAgainWithScroll && !pollingStopped) {
        pollAgainWithScroll = false;
        loadNewMessages(true);
      }
    }
  }

  form.addEventListener("submit", async function (event) {
    event.preventDefault();
    if (sending || !message.value.trim()) {
      return;
    }

    sending = true;
    clearError();
    sendButton.disabled = true;

    try {
      var response = await fetch(form.action, {
        method: "POST",
        credentials: "same-origin",
        body: new FormData(form),
        headers: {
          "Accept": "text/plain",
          "X-Requested-With": "XMLHttpRequest"
        }
      });

      if (handleAuthenticationFailure(response)) {
        return;
      }
      if (response.status !== 204) {
        showError(await response.text() || "The message could not be sent.");
        return;
      }

      message.value = "";
      resizeMessageBox();
      await loadNewMessages(true);
    } catch (error) {
      showError("The message could not be sent. Please try again.");
    } finally {
      sending = false;
      sendButton.disabled = false;
      message.focus();
    }
  });

  message.addEventListener("input", resizeMessageBox);
  message.addEventListener("keydown", function (event) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      if (!sending && message.value.trim()) {
        form.requestSubmit();
      }
    }
  });

  resizeMessageBox();
  scrollToBottom();
  pollingTimer = window.setInterval(function () {
    loadNewMessages(false);
  }, 2500);
});
