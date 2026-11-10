package dev.aayush.os;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Module 4: Personal AI Assistant Engine.
 * Supports switching between ChatGPT (OpenAI), Google Gemini, and Local/Custom (Ollama/LMStudio on Mac M4),
 * with an instant offline local heuristic fallback when no API key is configured.
 */
public final class PersonalAiAssistant {

    public enum Provider {
        GEMINI("GEMINI (2.5 FLASH)", "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent", "gemini-2.5-flash"),
        CHATGPT("CHATGPT (OPENAI)", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini"),
        LOCAL_MAC("LOCAL MAC M4 (OLLAMA)", "http://127.0.0.1:11434/v1/chat/completions", "mistral:7b");

        public final String label;
        public final String defaultEndpoint;
        public final String defaultModel;

        Provider(String l, String ep, String m) {
            this.label = l;
            this.defaultEndpoint = ep;
            this.defaultModel = m;
        }
    }

    public enum TaskType {
        TONE_PROFESSIONAL("You are Samsung Galaxy AI Style Writing Assist. Rewrite the provided message in a polite, formal, polished professional tone suitable for business or email. Output ONLY the rewritten text with zero preamble or notes."),
        TONE_CASUAL("You are Samsung Galaxy AI Style Writing Assist. Rewrite the provided message in a friendly, relaxed, natural conversational tone suitable for casual messaging. Output ONLY the rewritten text with zero preamble or notes."),
        FIX_GRAMMAR("You are Samsung Galaxy AI Style Writing Assist. Correct all spelling, typos, and grammatical errors in the user's text while strictly preserving the original tone, vocabulary, and meaning. Output ONLY the corrected text with zero preamble or notes."),
        TONE_CONCISE("You are Samsung Galaxy AI Style Writing Assist. Rewrite the user's text to be short, punchy, and concise, eliminating fluff. Output ONLY the rewritten text with zero preamble or notes."),
        SUMMARIZE("Summarize concisely in bullet points"),
        EXPLAIN_CODE("Explain this code or stack trace clearly for a senior developer"),
        GEN_COMMAND("Convert this request into an exact macOS/Linux/ADB terminal command"),
        REFACTOR("Suggest improvements, edge cases, and cleaner syntax");

        public final String systemInstruction;
        TaskType(String s) {
            this.systemInstruction = s;
        }
    }

    public interface Callback {
        void onResponse(String result);
    }

    private static final String PREF_NAME = "aayush_os_ai";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static Provider getSelectedProvider(Context ctx) {
        String name = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString("provider", Provider.LOCAL_MAC.name());
        try {
            return Provider.valueOf(name);
        } catch (Exception e) {
            return Provider.LOCAL_MAC;
        }
    }

    public static void setSelectedProvider(Context ctx, Provider p) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString("provider", p.name()).apply();
    }

    public static String getApiKey(Context ctx, Provider p) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString("key_" + p.name(), "");
    }

    public static String getCustomEndpoint(Context ctx, Provider p) {
        if (p == Provider.LOCAL_MAC) {
            String saved = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .getString("ep_" + p.name(), null);
            if (saved != null) return saved;
            String host = MacCompanion.getMacHost(ctx);
            if (!TextUtils.isEmpty(host) && !host.equals("127.0.0.1") && !host.equals("0.0.0.0")) {
                return "http://" + host + ":11434/v1/chat/completions";
            }
            return p.defaultEndpoint;
        }
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString("ep_" + p.name(), p.defaultEndpoint);
    }

    public static String getCustomModel(Context ctx, Provider p) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString("model_" + p.name(), p.defaultModel);
    }

    public static void saveConfig(Context ctx, Provider p, String apiKey, String endpoint, String model) {
        SharedPreferences.Editor ed = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        ed.putString("provider", p.name());
        if (apiKey != null) ed.putString("key_" + p.name(), apiKey.trim());
        if (!TextUtils.isEmpty(endpoint)) ed.putString("ep_" + p.name(), endpoint.trim());
        if (!TextUtils.isEmpty(model)) ed.putString("model_" + p.name(), model.trim());
        ed.apply();
    }

    public static void runTask(final Context ctx, final TaskType task, final String userInput, final Callback cb) {
        if (userInput == null || userInput.trim().isEmpty()) {
            cb.onResponse("Provide input text or paste from clipboard first.");
            return;
        }
        final Provider provider = getSelectedProvider(ctx);
        final String apiKey = getApiKey(ctx, provider);
        final String endpoint = getCustomEndpoint(ctx, provider);
        final String model = getCustomModel(ctx, provider);

        new Thread(() -> {
            // If GEMINI or CHATGPT is selected without an API key, run local deterministic analysis + prompt preview
            if (provider != Provider.LOCAL_MAC && TextUtils.isEmpty(apiKey)) {
                final String offlineOut = runLocalDeterministicEngine(provider, task, userInput.trim());
                mainHandler.post(() -> cb.onResponse(offlineOut));
                return;
            }

            try {
                String out;
                if (provider == Provider.GEMINI) {
                    out = callGeminiApi(endpoint, apiKey, task.systemInstruction, userInput.trim());
                } else {
                    out = callOpenAiCompatibleApi(endpoint, apiKey, model, task.systemInstruction, userInput.trim());
                }
                final String finalOut = out;
                mainHandler.post(() -> cb.onResponse(finalOut));
            } catch (Exception e) {
                final String fallback = runLocalDeterministicEngine(provider, task, userInput.trim())
                        + "\n\n[Remote Endpoint Note: " + e.getMessage() + "]";
                mainHandler.post(() -> cb.onResponse(fallback));
            }
        }).start();
    }

    private static String callGeminiApi(String baseUrl, String apiKey, String sysPrompt, String input) throws Exception {
        String urlStr = baseUrl + (baseUrl.contains("?") ? "&" : "?") + "key=" + apiKey;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        JSONObject textPart = new JSONObject().put("text", sysPrompt + ":\n\n" + input);
        JSONObject contentObj = new JSONObject().put("parts", new JSONArray().put(textPart));
        JSONObject payload = new JSONObject().put("contents", new JSONArray().put(contentObj));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String raw = readStream(is);
        conn.disconnect();

        if (code != 200) {
            throw new Exception("Gemini HTTP " + code + ": " + raw);
        }
        JSONObject resp = new JSONObject(raw);
        return resp.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text").trim();
    }

    private static String callOpenAiCompatibleApi(String endpoint, String apiKey, String model, String sysPrompt, String input) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (!TextUtils.isEmpty(apiKey)) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", sysPrompt));
        messages.put(new JSONObject().put("role", "user").put("content", input));

        JSONObject options = new JSONObject();
        options.put("num_thread", 2);

        JSONObject payload = new JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("options", options)
                .put("temperature", 0.3);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String raw = readStream(is);
        conn.disconnect();

        if (code != 200) {
            throw new Exception("HTTP " + code + ": " + raw);
        }
        JSONObject resp = new JSONObject(raw);
        return resp.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content").trim();
    }

    private static String runLocalDeterministicEngine(Provider p, TaskType task, String input) {
        if (task == TaskType.FIX_GRAMMAR) {
            String fixed = input.trim();
            fixed = fixed.replaceAll("(?i)\\bcant\\b", "can't")
                         .replaceAll("(?i)\\bdont\\b", "don't")
                         .replaceAll("(?i)\\bwont\\b", "won't")
                         .replaceAll("(?i)\\bidk\\b", "I don't know")
                         .replaceAll("(?i)\\btmrw\\b", "tomorrow")
                         .replaceAll("(?i)\\bbro\\b", "friend")
                         .replaceAll("(?i)\\bim\\b", "I am")
                         .replaceAll("(?i)\\bi\\b", "I")
                         .replaceAll("(?i)\\bu\\b", "you")
                         .replaceAll("(?i)\\bur\\b", "your");
            if (fixed.length() > 0) {
                fixed = Character.toUpperCase(fixed.charAt(0)) + fixed.substring(1);
            }
            if (!fixed.endsWith(".") && !fixed.endsWith("!") && !fixed.endsWith("?")) {
                fixed += ".";
            }
            return fixed;
        }

        if (task == TaskType.TONE_PROFESSIONAL) {
            String clean = runLocalDeterministicEngine(p, TaskType.FIX_GRAMMAR, input);
            return "Good day,\n\nI am writing to communicate regarding: " + clean + "\n\nThank you for your consideration.\nBest regards.";
        }

        if (task == TaskType.TONE_CASUAL) {
            String clean = runLocalDeterministicEngine(p, TaskType.FIX_GRAMMAR, input);
            return "Hey! Just wanted to share: " + clean + " Catch you later!";
        }

        if (task == TaskType.TONE_CONCISE) {
            return input.trim().replaceAll("\\s+", " ");
        }

        SmartClipboard.Analysis clip = SmartClipboard.classify(input);
        StringBuilder sb = new StringBuilder();
        sb.append("// LOCAL ON-DEVICE ENGINE [").append(p.label).append("]\n");

        if (task == TaskType.GEN_COMMAND) {
            String low = input.toLowerCase(Locale.US);
            if (low.contains("port") || low.contains("kill")) {
                sb.append("$ lsof -ti :8080 | xargs kill -9");
            } else if (low.contains("git") || low.contains("undo") || low.contains("commit")) {
                sb.append("$ git reset --soft HEAD~1 && git status");
            } else if (low.contains("adb") || low.contains("log") || low.contains("pixel")) {
                sb.append("$ adb logcat -v time *:E | grep -i aayush");
            } else if (low.contains("disk") || low.contains("size") || low.contains("space")) {
                sb.append("$ du -sh * | sort -hr | head -n 15");
            } else {
                sb.append("$ curl -sSL \"").append(input.replace("\"", "")).append("\" | jq .");
            }
            return sb.toString();
        }

        String[] lines = input.split("\n");
        String[] words = input.trim().split("\\s+");
        sb.append(String.format(Locale.US, "• Detected Type : %s (%d words, %d lines)\n",
                clip.badgeLabel, words.length, lines.length));
        sb.append("• Analysis      : ").append(clip.summary).append("\n");
        if (!TextUtils.isEmpty(clip.transformedText) && !clip.transformedText.equals(input)) {
            sb.append("• Processed     :\n").append(clip.transformedText).append("\n");
        }
        sb.append("\n(Tip: Add your ").append(p.name())
          .append(" API key or start Ollama on 192.168.0.110:11434 for live LLM completions.)");
        return sb.toString();
    }

    private static String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }
}
