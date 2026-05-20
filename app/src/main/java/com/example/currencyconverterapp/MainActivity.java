package com.example.currencyconverterapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.android.volley.*;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.data.*;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    EditText amountInput;
    Spinner fromCurrency, toCurrency;
    Button convertButton;
    TextView resultText;
    ListView historyList;
    LineChart chart;

    ArrayList<String> conversionHistory;
    ArrayAdapter<String> historyAdapter;

    SharedPreferences prefs;
    SharedPreferences.Editor editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        amountInput = findViewById(R.id.amountInput);
        fromCurrency = findViewById(R.id.fromCurrency);
        toCurrency = findViewById(R.id.toCurrency);
        convertButton = findViewById(R.id.convertButton);
        resultText = findViewById(R.id.resultText);
        historyList = findViewById(R.id.historyList);
        chart = findViewById(R.id.lineChart);

        String[] currencies = {"USD", "PKR", "EUR", "INR"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currencies);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fromCurrency.setAdapter(adapter);
        toCurrency.setAdapter(adapter);

        prefs = getSharedPreferences("CurrencyPrefs", MODE_PRIVATE);
        editor = prefs.edit();

        conversionHistory = new ArrayList<>();
        historyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, conversionHistory);
        historyList.setAdapter(historyAdapter);

        convertButton.setOnClickListener(v -> {
            String amountStr = amountInput.getText().toString();
            if (amountStr.isEmpty()) {
                resultText.setText(getString(R.string.enter_amount));
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                resultText.setText(getString(R.string.invalid_number));
                return;
            }

            String from = fromCurrency.getSelectedItem().toString();
            String to = toCurrency.getSelectedItem().toString();

            // ✅ Frankfurter API for latest conversion
            String url = "https://api.frankfurter.app/latest?amount=" + amount + "&from=" + from + "&to=" + to;

            RequestQueue queue = Volley.newRequestQueue(this);
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                    response -> {
                        try {
                            if (response.has("rates")) {
                                JSONObject ratesObj = response.getJSONObject("rates");
                                if (ratesObj.has(to)) {
                                    double converted = ratesObj.getDouble(to);

                                    String result = String.format(Locale.getDefault(),
                                            "%.2f %s = %.2f %s", amount, from, converted, to);
                                    resultText.setText(result);

                                    conversionHistory.add(result);
                                    historyAdapter.notifyDataSetChanged();

                                    editor.putFloat("lastRate_" + from + "_" + to, (float) converted / (float) amount);
                                    editor.apply();

                                    // ✅ Frankfurter API for last 7 days history
                                    Calendar cal = Calendar.getInstance();
                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                                    String endDate = sdf.format(cal.getTime());
                                    cal.add(Calendar.DAY_OF_YEAR, -7);
                                    String startDate = sdf.format(cal.getTime());

                                    String historyUrl = "https://api.frankfurter.app/" + startDate + ".." + endDate +
                                            "?from=" + from + "&to=" + to;

                                    JsonObjectRequest historyRequest = new JsonObjectRequest(Request.Method.GET, historyUrl, null,
                                            histResponse -> {
                                                try {
                                                    if (histResponse.has("rates")) {
                                                        JSONObject ratesObjHist = histResponse.getJSONObject("rates");
                                                        ArrayList<Entry> entries = new ArrayList<>();
                                                        int day = 1;

                                                        Iterator<String> keys = ratesObjHist.keys();
                                                        while (keys.hasNext()) {
                                                            String date = keys.next();
                                                            JSONObject dayRates = ratesObjHist.getJSONObject(date);
                                                            if (dayRates.has(to)) {
                                                                double histRate = dayRates.getDouble(to);
                                                                entries.add(new Entry(day, (float) histRate));
                                                                day++;
                                                            }
                                                        }

                                                        if (!entries.isEmpty()) {
                                                            LineDataSet dataSet = new LineDataSet(entries, from + " → " + to + " Trend (7 days)");
                                                            dataSet.setColor(ContextCompat.getColor(this, android.R.color.holo_blue_light));
                                                            dataSet.setValueTextColor(ContextCompat.getColor(this, android.R.color.white));

                                                            LineData lineData = new LineData(dataSet);
                                                            chart.setData(lineData);

                                                            Description desc = new Description();
                                                            desc.setText(getString(R.string.trend_graph));
                                                            chart.setDescription(desc);

                                                            chart.invalidate();
                                                        } else {
                                                            chart.clear();
                                                            resultText.setText(getString(R.string.no_history));
                                                        }
                                                    }
                                                } catch (Exception e) {
                                                    resultText.setText(getString(R.string.error_parsing));
                                                }
                                            },
                                            error -> resultText.setText(getString(R.string.error_fetching))
                                    );

                                    queue.add(historyRequest);

                                } else {
                                    resultText.setText("Currency not found in response");
                                }
                            } else {
                                resultText.setText(getString(R.string.api_error));
                            }
                        } catch (Exception e) {
                            resultText.setText(getString(R.string.error_parsing));
                        }
                    },
                    error -> {
                        float savedRate = prefs.getFloat("lastRate_" + from + "_" + to, 0f);
                        if (savedRate != 0f) {
                            double converted = amount * savedRate;
                            String result = String.format(Locale.getDefault(),
                                    "%.2f %s = %.2f %s (offline)", amount, from, converted, to);
                            resultText.setText(result);

                            conversionHistory.add(result);
                            historyAdapter.notifyDataSetChanged();
                        } else {
                            resultText.setText(getString(R.string.api_error));
                        }
                    }
            );

            queue.add(request);
        });
    }
}
