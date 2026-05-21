package com.example.currencyconverterapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.*;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.util.*;

public class MainActivity extends AppCompatActivity {

    EditText amountInput;
    Spinner fromCurrency, toCurrency;
    Button convertButton;
    TextView resultText;
    ListView historyList;

    ArrayList<String> conversionHistory;
    ArrayAdapter<String> historyAdapter;

    SharedPreferences prefs;
    SharedPreferences.Editor editor;

    // ✅ Your UniRateAPI key
    private static final String API_KEY = "G14dYlMeHCzLjDyrcDlO3CMoEI03tBCS6XLmNploOv0eTNmOOHm1m2Gre0632NQD";

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

        ArrayAdapter<String> adapter = getCurrencyAdapter();
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
                resultText.setText(R.string.enter_amount);
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                resultText.setText(R.string.invalid_number);
                return;
            }

            String from = fromCurrency.getSelectedItem().toString();
            String to = toCurrency.getSelectedItem().toString();

            // ✅ UniRateAPI latest conversion
            String url = "https://api.unirate.live/latest?base=" + from + "&symbols=" + to + "&apikey=" + API_KEY;

            RequestQueue queue = Volley.newRequestQueue(this);
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                    response -> {
                        try {
                            if (response.has("rates")) {
                                JSONObject ratesObj = response.getJSONObject("rates");
                                if (ratesObj.has(to)) {
                                    double rate = ratesObj.getDouble(to);
                                    double converted = amount * rate;

                                    String result = String.format(Locale.getDefault(),
                                            "%.2f %s = %.2f %s", amount, from, converted, to);
                                    resultText.setText(result);

                                    // ✅ Save conversion in history
                                    conversionHistory.add(result);
                                    historyAdapter.notifyDataSetChanged();

                                    // ✅ Save last rate for offline use
                                    editor.putFloat("lastRate_" + from + "_" + to, (float) rate);
                                    editor.apply();

                                } else {
                                    resultText.setText(R.string.currency_not_found);
                                }
                            } else {
                                resultText.setText(R.string.api_error);
                            }
                        } catch (Exception e) {
                            resultText.setText(R.string.error_parsing);
                        }
                    },
                    error -> {
                        // ✅ Offline fallback
                        float savedRate = prefs.getFloat("lastRate_" + from + "_" + to, 0f);
                        if (savedRate != 0f) {
                            double converted = amount * savedRate;
                            String result = String.format(Locale.getDefault(),
                                    "%.2f %s = %.2f %s (offline)", amount, from, converted, to);
                            resultText.setText(result);

                            conversionHistory.add(result);
                            historyAdapter.notifyDataSetChanged();
                        } else {
                            resultText.setText(R.string.api_error);
                        }
                    }
            );

            queue.add(request);
        });
    }

    // ✅ Currency list for dropdown
    private ArrayAdapter<String> getCurrencyAdapter() {
        String[] currencies = {
                "USD","PKR","EUR","INR","GBP","JPY","AUD","CAD","CNY","SAR","AED","TRY","NZD","ZAR",
                "CHF","SGD","HKD","KRW","NOK","SEK","DKK","MXN","BRL","RUB","THB","IDR","MYR","PHP",
                "PLN","HUF","CZK","ILS","EGP","BDT","LKR","NGN","KES","GHS","MAD","TND","UAH","VND",
                "ARS","CLP","COP","PEN","KWD","OMR","QAR","BHD","IRR","IQD","AFN","AZN","UZS","KZT"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currencies);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }
}
