package com.japanledger.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.view.Gravity;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int MICROPHONE_PERMISSION_REQUEST = 25;
    private static final int EXPORT_CSV_REQUEST = 40;
    private static final String[] CATEGORIES = {"餐饮", "购物", "交通", "其他"};
    private static final int INK = Color.rgb(23, 33, 27);
    private static final int PAPER = Color.rgb(245, 243, 237);
    private static final int MINT = Color.rgb(185, 228, 204);
    private static final int MUTED = Color.rgb(102, 110, 104);
    private static final int LINE = Color.rgb(222, 220, 213);
    private static final String PREFS = "travel_ledger";
    private static final String RECORDS_KEY = "records";
    private static final String MANUAL_CATEGORY_MIGRATION_KEY = "manual_categories_v1";

    private LinearLayout page;
    private EditText entryInput;
    private Button micButton;
    private SpeechRecognizer speechRecognizer;
    private boolean listening;
    private Screen currentScreen = Screen.HOME;
    private final DecimalFormat amountFormat = new DecimalFormat("#,##0.##");
    private List<Record> pendingExportRecords;

    private enum Screen { HOME, ENTRY, MANAGE }

    private static class Record {
        String id;
        String item;
        String category;
        double amount;
        long createdAt;

        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("item", item);
            json.put("category", category);
            json.put("amount", amount);
            json.put("createdAt", createdAt);
            return json;
        }

        static Record fromJson(JSONObject json) {
            Record record = new Record();
            record.id = json.optString("id", String.valueOf(System.nanoTime()));
            record.item = json.optString("item", "未命名消费");
            record.category = json.optString("category", "其他");
            record.amount = json.optDouble("amount", 0);
            record.createdAt = json.optLong("createdAt", System.currentTimeMillis());
            return record;
        }
    }

    private static class ParsedExpense {
        String item;
        String category;
        double amount;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Window window = getWindow();
            window.setStatusBarColor(PAPER);
            window.setNavigationBarColor(PAPER);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            migrateExistingRecordsToManualCategories();
            showHome();
        } catch (Throwable startupError) {
            showSafeFallback();
        }
    }

    private void showSafeFallback() {
        LinearLayout fallback = new LinearLayout(this);
        fallback.setOrientation(LinearLayout.VERTICAL);
        fallback.setGravity(Gravity.CENTER);
        fallback.setPadding(dp(28), dp(28), dp(28), dp(28));
        fallback.setBackgroundColor(PAPER);
        TextView title = text("旅账", 30, INK, true);
        fallback.addView(title);
        TextView message = text("应用已进入兼容模式\n请重新打开一次", 16, MUTED, false);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, dp(18), 0, 0);
        fallback.addView(message);
        setContentView(fallback);
    }

    private void preparePage(Screen screen) {
        if (currentScreen == Screen.ENTRY && screen != Screen.ENTRY) releaseSpeechRecognizer();
        currentScreen = screen;
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(20), dp(24), dp(24));
        page.setBackgroundColor(PAPER);
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            int safeTop = insets.getSystemWindowInsetTop();
            int safeBottom = insets.getSystemWindowInsetBottom();
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    safeTop = Math.max(safeTop, cutout.getSafeInsetTop());
                    safeBottom = Math.max(safeBottom, cutout.getSafeInsetBottom());
                }
            }
            view.setPadding(dp(24), Math.max(dp(20), safeTop + dp(12)),
                    dp(24), Math.max(dp(24), safeBottom + dp(12)));
            return insets;
        });
        setContentView(page);
        page.requestApplyInsets();
    }

    private void showHome() {
        preparePage(Screen.HOME);

        TextView mark = text("旅账", 16, INK, true);
        mark.setLetterSpacing(0.16f);
        page.addView(mark, lpMatch(dp(40)));

        Space top = new Space(this);
        page.addView(top, new LinearLayout.LayoutParams(1, 0, 0.7f));

        TextView title = text("今天，花了什么？", 31, INK, true);
        page.addView(title, lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView subtitle = text("日本旅行 · 本地记账", 14, MUTED, false);
        subtitle.setPadding(0, dp(8), 0, dp(28));
        page.addView(subtitle);

        Button add = giantButton("＋  记账", INK, Color.WHITE);
        add.setOnClickListener(v -> showEntry());
        page.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.5f));

        Space gap = new Space(this);
        page.addView(gap, lpMatch(dp(16)));

        Button manage = giantButton("管理  →", MINT, INK);
        manage.setOnClickListener(v -> showManage());
        page.addView(manage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Space bottom = new Space(this);
        page.addView(bottom, new LinearLayout.LayoutParams(1, 0, 0.45f));
    }

    private void showEntry() {
        preparePage(Screen.ENTRY);
        addHeader("记一笔");

        TextView hint = text("说一句，或者写下来", 27, INK, true);
        hint.setPadding(0, dp(24), 0, dp(8));
        page.addView(hint);
        TextView example = text("例如：拉面 1,200 日元", 14, MUTED, false);
        example.setPadding(0, 0, 0, dp(20));
        page.addView(example);

        entryInput = new EditText(this);
        entryInput.setTextSize(19);
        entryInput.setTextColor(INK);
        entryInput.setHintTextColor(Color.rgb(157, 161, 157));
        entryInput.setHint("输入消费内容和金额…");
        entryInput.setGravity(Gravity.TOP | Gravity.START);
        entryInput.setPadding(dp(20), dp(18), dp(20), dp(18));
        entryInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        entryInput.setBackground(roundRect(Color.WHITE, 22, LINE, 1));
        page.addView(entryInput, lpMatch(dp(148)));

        micButton = button("●  按下说话", MINT, INK, 19, 22);
        LinearLayout.LayoutParams micParams = lpMatch(dp(72));
        micParams.setMargins(0, dp(16), 0, dp(12));
        page.addView(micButton, micParams);
        micButton.setOnClickListener(v -> startSpeech());

        Button parse = button("识别这笔消费  →", INK, Color.WHITE, 18, 22);
        page.addView(parse, lpMatch(dp(72)));
        parse.setOnClickListener(v -> parseAndConfirm(entryInput.getText().toString()));

        TextView local = text("信息只保存在这台手机里", 12, MUTED, false);
        local.setGravity(Gravity.CENTER);
        local.setPadding(0, dp(20), 0, 0);
        page.addView(local);
    }

    private void showManage() {
        preparePage(Screen.MANAGE);
        addHeader("所有记录");

        List<Record> records = loadRecords();
        double total = 0;
        for (Record record : records) total += record.amount;

        LinearLayout totalCard = new LinearLayout(this);
        totalCard.setOrientation(LinearLayout.VERTICAL);
        totalCard.setGravity(Gravity.CENTER_VERTICAL);
        totalCard.setPadding(dp(22), dp(20), dp(22), dp(20));
        totalCard.setBackground(roundRect(INK, 24, INK, 0));
        LinearLayout.LayoutParams totalParams = lpMatch(dp(116));
        totalParams.setMargins(0, dp(18), 0, dp(14));
        page.addView(totalCard, totalParams);

        TextView totalLabel = text("总共花费", 13, Color.rgb(195, 203, 197), false);
        totalCard.addView(totalLabel);
        TextView totalValue = text("¥ " + amountFormat.format(total), 32, Color.WHITE, true);
        totalValue.setPadding(0, dp(5), 0, 0);
        totalCard.addView(totalValue);

        if (records.isEmpty()) {
            Space spacer = new Space(this);
            page.addView(spacer, lpMatch(dp(56)));
            TextView empty = text("还没有记录\n去记下旅行的第一笔吧", 17, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(dp(5), 1f);
            page.addView(empty, lpMatch(ViewGroup.LayoutParams.WRAP_CONTENT));
            Button first = button("记一笔", MINT, INK, 17, 18);
            LinearLayout.LayoutParams firstParams = lpMatch(dp(62));
            firstParams.setMargins(0, dp(28), 0, 0);
            page.addView(first, firstParams);
            first.setOnClickListener(v -> showEntry());
            return;
        }

        Button export = button("导出 CSV  ↓", MINT, INK, 16, 17);
        LinearLayout.LayoutParams exportParams = lpMatch(dp(54));
        exportParams.setMargins(0, 0, 0, dp(12));
        page.addView(export, exportParams);
        export.setOnClickListener(v -> chooseCsvLocation(records));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, dp(20));
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        addCategorySummary(list, records);
        List<String> sections = new ArrayList<>();
        Collections.addAll(sections, CATEGORIES);
        sections.add("未分类");
        for (String category : sections) {
            List<Record> categoryRecords = new ArrayList<>();
            double subtotal = 0;
            for (Record record : records) {
                if (category.equals(record.category)) {
                    categoryRecords.add(record);
                    subtotal += record.amount;
                }
            }
            if (categoryRecords.isEmpty()) continue;
            list.addView(categoryHeader(category, categoryRecords.size(), subtotal));
            for (Record record : categoryRecords) list.addView(recordCard(record));
        }
    }

    private void addCategorySummary(LinearLayout parent, List<Record> records) {
        TextView heading = text("分类花费", 14, INK, true);
        heading.setPadding(dp(2), dp(2), 0, dp(10));
        parent.addView(heading);

        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(firstRow, lpMatch(dp(76)));
        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams secondParams = lpMatch(dp(76));
        secondParams.setMargins(0, dp(8), 0, dp(18));
        parent.addView(secondRow, secondParams);

        for (int i = 0; i < CATEGORIES.length; i++) {
            String category = CATEGORIES[i];
            double subtotal = 0;
            for (Record record : records) if (category.equals(record.category)) subtotal += record.amount;
            LinearLayout card = categorySummaryCard(category, subtotal);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, dp(76), 1);
            if (i % 2 == 0) cardParams.setMargins(0, 0, dp(4), 0);
            else cardParams.setMargins(dp(4), 0, 0, 0);
            (i < 2 ? firstRow : secondRow).addView(card, cardParams);
        }

        int unclassified = 0;
        for (Record record : records) if ("未分类".equals(record.category)) unclassified++;
        TextView instruction = text(unclassified > 0
                ? unclassified + " 笔待归类 · 点击下方记录选择类别"
                : "点击任意记录可重新归类", 13, MUTED, false);
        instruction.setPadding(dp(2), 0, 0, dp(2));
        parent.addView(instruction);
    }

    private LinearLayout categorySummaryCard(String category, double subtotal) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(10), dp(15), dp(10));
        card.setBackground(roundRect(Color.WHITE, 16, LINE, 1));
        TextView label = text(categoryIcon(category) + "  " + category, 13, MUTED, false);
        card.addView(label);
        TextView value = text("¥ " + amountFormat.format(subtotal), 18, INK, true);
        value.setPadding(0, dp(4), 0, 0);
        card.addView(value);
        return card;
    }

    private View categoryHeader(String category, int count, double subtotal) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(12), dp(2), dp(9));
        TextView title = text(category + "  " + count + " 笔", 15, INK, true);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView total = text("小计 ¥" + amountFormat.format(subtotal), 13, MUTED, false);
        row.addView(total);
        return row;
    }

    private View recordCard(Record record) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(10), dp(14));
        card.setBackground(roundRect(Color.WHITE, 18, LINE, 1));
        LinearLayout.LayoutParams params = lpMatch(dp(86));
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);

        TextView icon = text(categoryIcon(record.category), 23, INK, false);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundRect(MINT, 14, MINT, 0));
        card.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(13), 0, dp(8), 0);
        card.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView item = text(record.item, 16, INK, true);
        item.setSingleLine(true);
        words.addView(item);
        String date = new SimpleDateFormat("MM月dd日  HH:mm", Locale.CHINA).format(new Date(record.createdAt));
        TextView meta = text(record.category + " · " + date, 12, MUTED, false);
        meta.setPadding(0, dp(4), 0, 0);
        words.addView(meta);

        TextView amount = text("¥" + amountFormat.format(record.amount), 17, INK, true);
        card.addView(amount);
        TextView remove = text("×", 23, MUTED, false);
        remove.setGravity(Gravity.CENTER);
        remove.setContentDescription("删除" + record.item);
        remove.setOnClickListener(v -> confirmDelete(record));
        card.addView(remove, new LinearLayout.LayoutParams(dp(40), dp(48)));
        card.setContentDescription(record.item + "，当前分类" + record.category + "，点击修改分类");
        card.setOnClickListener(v -> showCategoryDialog(record));
        return card;
    }

    private void addHeader(String titleText) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 38, INK, false);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setContentDescription("返回");
        back.setOnClickListener(v -> showHome());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(48)));
        TextView title = text(titleText, 17, INK, true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView home = text("旅账", 13, MUTED, true);
        home.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(home, new LinearLayout.LayoutParams(dp(52), dp(48)));
        page.addView(header, lpMatch(dp(52)));
    }

    private void startSpeech() {
        if (listening) {
            if (speechRecognizer != null) speechRecognizer.stopListening();
            setMicState(false, "正在识别…");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MICROPHONE_PERMISSION_REQUEST);
            return;
        }
        beginListening();
    }

    private void beginListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "未找到系统语音识别服务，请在系统设置中启用语音识别服务", Toast.LENGTH_LONG).show();
            return;
        }
        releaseSpeechRecognizer();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                setMicState(true, "●  正在听，请说话");
            }
            @Override public void onBeginningOfSpeech() {
                setMicState(true, "●  正在听…点击结束");
            }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() {
                setMicState(false, "正在识别…");
            }
            @Override public void onError(int error) {
                setMicState(false, "●  按下说话");
                String message;
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    message = "没有麦克风权限，请在系统设置中允许录音";
                } else if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                    message = "没有听清，请靠近手机再说一次";
                } else if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    message = "没有听到声音，请重试";
                } else if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                    message = "语音服务网络异常，也可以直接输入文字";
                } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    message = "语音服务正忙，请稍后再试";
                } else {
                    message = "语音识别失败，请重试或直接输入文字";
                }
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
            @Override public void onResults(Bundle results) {
                setMicState(false, "●  按下说话");
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty() && entryInput != null) {
                    entryInput.setText(matches.get(0));
                    entryInput.setSelection(entryInput.length());
                    parseAndConfirm(matches.get(0));
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty() && entryInput != null) {
                    entryInput.setText(matches.get(0));
                    entryInput.setSelection(entryInput.length());
                }
            }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        setMicState(true, "正在启动麦克风…");
        try {
            speechRecognizer.startListening(intent);
        } catch (RuntimeException error) {
            setMicState(false, "●  按下说话");
            Toast.makeText(this, "无法启动语音服务，请检查系统语音识别设置", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MICROPHONE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                beginListening();
            } else {
                Toast.makeText(this, "需要麦克风权限才能使用语音记账", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void chooseCsvLocation(List<Record> records) {
        pendingExportRecords = new ArrayList<>(records);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        String date = new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "旅账-" + date + ".csv");
        try {
            startActivityForResult(intent, EXPORT_CSV_REQUEST);
        } catch (RuntimeException error) {
            Toast.makeText(this, "无法打开系统文件保存窗口", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EXPORT_CSV_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            exportCsv(data.getData(), pendingExportRecords == null ? loadRecords() : pendingExportRecords);
            pendingExportRecords = null;
        }
    }

    private void exportCsv(Uri uri, List<Record> records) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        DecimalFormat csvAmount = new DecimalFormat("0.##");
        try (OutputStream output = getContentResolver().openOutputStream(uri);
             OutputStreamWriter writer = output == null ? null : new OutputStreamWriter(output, "UTF-8")) {
            if (writer == null) throw new IOException("Cannot open export destination");
            writer.write('\uFEFF');
            writer.write("日期时间,消费项目,类别,金额（日元）\r\n");
            for (Record record : records) {
                writer.write(csvCell(dateFormat.format(new Date(record.createdAt))));
                writer.write(",");
                writer.write(csvCell(record.item));
                writer.write(",");
                writer.write(csvCell(normalizeCategory(record.category)));
                writer.write(",");
                writer.write(csvAmount.format(record.amount));
                writer.write("\r\n");
            }
            writer.flush();
            Toast.makeText(this, "CSV 已导出", Toast.LENGTH_LONG).show();
        } catch (IOException error) {
            Toast.makeText(this, "导出失败，请重新选择保存位置", Toast.LENGTH_LONG).show();
        }
    }

    private String csvCell(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void setMicState(boolean isListening, String label) {
        listening = isListening;
        if (micButton != null) {
            micButton.setText(label);
            micButton.setBackground(roundRect(isListening ? INK : MINT, 22,
                    isListening ? INK : MINT, 0));
            micButton.setTextColor(isListening ? Color.WHITE : INK);
        }
    }

    private void releaseSpeechRecognizer() {
        listening = false;
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }

    private void parseAndConfirm(String raw) {
        raw = raw == null ? "" : raw.trim();
        if (raw.isEmpty()) {
            entryInput.setError("先说或写一笔消费");
            return;
        }
        ParsedExpense parsed = parseExpense(raw);
        if (parsed == null) {
            entryInput.setError("没有识别到金额，请加上数字，例如：寿司 2800 日元");
            return;
        }
        showConfirmDialog(parsed);
    }

    private ParsedExpense parseExpense(String raw) {
        String normalized = normalizeChineseNumber(raw);
        Pattern pattern = Pattern.compile("(?:[¥￥]\\s*)?([0-9][0-9,，]*(?:\\.[0-9]{1,2})?)\\s*(日元|円|元|块|人民币|RMB|JPY|CNY)?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(normalized);
        MatchResult last = null;
        while (matcher.find()) last = matcher.toMatchResult();
        if (last == null) return null;

        double amount;
        try {
            amount = Double.parseDouble(last.group(1).replace(",", "").replace("，", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        if (amount <= 0) return null;

        String item = (normalized.substring(0, last.start()) + " " + normalized.substring(last.end())).trim();
        item = item.replaceAll("[，,。.!！?？]", " ")
                .replaceAll("^(我|我们)?(今天|刚才|刚刚)?(在[^ ]+)?(花了|花|消费了|消费|付了|支付了|买了|买|吃了|吃)", "")
                .replaceAll("(一共|总共|合计|花了|消费了|支付了|付款|付了)$", "")
                .replaceAll("\\s+", " ").trim();
        if (item.isEmpty()) item = "未命名消费";

        ParsedExpense result = new ParsedExpense();
        result.item = item;
        result.amount = amount;
        result.category = "未分类";
        return result;
    }

    private String normalizeChineseNumber(String raw) {
        String result = raw;
        Pattern simple = Pattern.compile("([一二两三四五六七八九])([百千])([一二三四五六七八九]?)");
        Matcher matcher = simple.matcher(result);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            int high = chineseDigit(matcher.group(1));
            int unit = "百".equals(matcher.group(2)) ? 100 : 1000;
            int low = matcher.group(3).isEmpty() ? 0 : chineseDigit(matcher.group(3)) * (unit == 1000 ? 100 : 10);
            matcher.appendReplacement(buffer, String.valueOf(high * unit + low));
        }
        matcher.appendTail(buffer);
        result = buffer.toString();

        Pattern tens = Pattern.compile("([一二三四五六七八九]?)十([一二三四五六七八九]?)");
        matcher = tens.matcher(result);
        buffer = new StringBuffer();
        while (matcher.find()) {
            int high = matcher.group(1).isEmpty() ? 1 : chineseDigit(matcher.group(1));
            int low = matcher.group(2).isEmpty() ? 0 : chineseDigit(matcher.group(2));
            matcher.appendReplacement(buffer, String.valueOf(high * 10 + low));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private int chineseDigit(String digit) {
        String digits = "零一二三四五六七八九";
        if ("两".equals(digit)) return 2;
        return Math.max(0, digits.indexOf(digit));
    }

    private void showConfirmDialog(ParsedExpense parsed) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(8), dp(24), 0);

        TextView detected = text("保存后可在管理页手动归类", 13, MUTED, false);
        detected.setPadding(0, 0, 0, dp(12));
        content.addView(detected);

        TextView itemLabel = text("消费项目", 12, MUTED, false);
        content.addView(itemLabel);
        EditText itemInput = dialogInput(parsed.item, InputType.TYPE_CLASS_TEXT);
        content.addView(itemInput, lpMatch(dp(54)));

        TextView amountLabel = text("金额（日元）", 12, MUTED, false);
        amountLabel.setPadding(0, dp(14), 0, 0);
        content.addView(amountLabel);
        EditText amountInput = dialogInput(amountFormat.format(parsed.amount).replace(",", ""),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        content.addView(amountInput, lpMatch(dp(54)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("确认这笔消费")
                .setView(content)
                .setNegativeButton("返回修改", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(INK);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MUTED);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String item = itemInput.getText().toString().trim();
                double amount;
                try {
                    amount = Double.parseDouble(amountInput.getText().toString().replace(",", ""));
                } catch (NumberFormatException e) {
                    amountInput.setError("请输入正确金额");
                    return;
                }
                if (item.isEmpty()) {
                    itemInput.setError("请输入消费项目");
                    return;
                }
                if (amount <= 0) {
                    amountInput.setError("金额需要大于 0");
                    return;
                }
                Record record = new Record();
                record.id = String.valueOf(System.currentTimeMillis());
                record.item = item;
                record.category = parsed.category;
                record.amount = amount;
                record.createdAt = System.currentTimeMillis();
                saveRecord(record);
                dialog.dismiss();
                Toast.makeText(this, "已保存 · ¥" + amountFormat.format(amount), Toast.LENGTH_SHORT).show();
                showHome();
            });
        });
        dialog.show();
    }

    private EditText dialogInput(String value, int inputType) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setTextSize(17);
        input.setTextColor(INK);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(roundRect(Color.rgb(248, 247, 243), 12, LINE, 1));
        return input;
    }

    private void showCategoryDialog(Record record) {
        int checked = -1;
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equals(record.category)) {
                checked = i;
                break;
            }
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("给“" + record.item + "”归类")
                .setSingleChoiceItems(CATEGORIES, checked, null)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int selected = dialog.getListView().getCheckedItemPosition();
            if (selected < 0 || selected >= CATEGORIES.length) {
                Toast.makeText(this, "请选择一个类别", Toast.LENGTH_SHORT).show();
                return;
            }
            updateRecordCategory(record.id, CATEGORIES[selected]);
            dialog.dismiss();
            showManage();
        }));
        dialog.show();
    }

    private void confirmDelete(Record record) {
        new AlertDialog.Builder(this)
                .setTitle("删除这笔记录？")
                .setMessage(record.item + "  ·  ¥" + amountFormat.format(record.amount))
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    deleteRecord(record.id);
                    showManage();
                })
                .show();
    }

    private List<Record> loadRecords() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String raw = prefs.getString(RECORDS_KEY, "[]");
        List<Record> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                Record record = Record.fromJson(array.getJSONObject(i));
                record.category = normalizeCategory(record.category);
                result.add(record);
            }
        } catch (JSONException ignored) {
            // A damaged local entry should not prevent the app from opening.
        }
        Collections.sort(result, (a, b) -> Long.compare(b.createdAt, a.createdAt));
        return result;
    }

    private void migrateExistingRecordsToManualCategories() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(MANUAL_CATEGORY_MIGRATION_KEY, false)) return;
        List<Record> records = loadRecords();
        for (Record record : records) record.category = "未分类";
        persistRecords(records);
        prefs.edit().putBoolean(MANUAL_CATEGORY_MIGRATION_KEY, true).apply();
    }

    private void saveRecord(Record record) {
        List<Record> records = loadRecords();
        records.add(record);
        persistRecords(records);
    }

    private void deleteRecord(String id) {
        List<Record> records = loadRecords();
        records.removeIf(record -> record.id.equals(id));
        persistRecords(records);
    }

    private void updateRecordCategory(String id, String category) {
        List<Record> records = loadRecords();
        for (Record record : records) {
            if (record.id.equals(id)) {
                record.category = category;
                break;
            }
        }
        persistRecords(records);
    }

    private void persistRecords(List<Record> records) {
        JSONArray array = new JSONArray();
        for (Record record : records) {
            try { array.put(record.toJson()); } catch (JSONException ignored) { }
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(RECORDS_KEY, array.toString()).apply();
    }

    private String categoryIcon(String category) {
        switch (category) {
            case "餐饮": return "餐";
            case "交通": return "行";
            case "购物": return "购";
            case "未分类": return "待";
            default: return "记";
        }
    }

    private String normalizeCategory(String category) {
        if ("餐饮".equals(category) || "购物".equals(category) || "交通".equals(category)
                || "其他".equals(category) || "未分类".equals(category)) return category;
        return "其他";
    }

    private Button giantButton(String label, int background, int foreground) {
        Button button = button(label, background, foreground, 26, 28);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(28), 0, dp(28), 0);
        return button;
    }

    private Button button(String label, int background, int foreground, int size, int radius) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(size);
        button.setTextColor(foreground);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setBackground(roundRect(background, radius, background, 0));
        button.setStateListAnimator(null);
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.create("sans", Typeface.BOLD));
        return view;
    }

    private GradientDrawable roundRect(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams lpMatch(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (currentScreen == Screen.HOME) super.onBackPressed();
        else showHome();
    }

    @Override
    protected void onDestroy() {
        releaseSpeechRecognizer();
        super.onDestroy();
    }
}
