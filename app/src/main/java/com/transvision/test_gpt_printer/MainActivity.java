package com.transvision.test_gpt_printer;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.lvrenyang.io.Canvas;
import com.lvrenyang.io.Pos;
import com.transvision.test_gpt_printer.service.BluetoothService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static com.transvision.test_gpt_printer.extra.Constants.BLUETOOTH_RESULT;
import static com.transvision.test_gpt_printer.extra.Constants.CONNECTED;
import static com.transvision.test_gpt_printer.extra.Constants.DISCONNECTED;
import static com.transvision.test_gpt_printer.extra.Constants.PRINTER_MSG;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int RequestPermissionCode = 1;

    Pos mPos = BluetoothService.mPos;
    Canvas mCanvas = BluetoothService.mCanvas;
    ExecutorService es = BluetoothService.es;

    Button btn_text_record, btn_image_record;

    private float yaxis = 0;
    private String splchar="";
    private Bitmap barcode;
    private ArrayList<String> res;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btn_text_record = findViewById(R.id.print_text_btn);
        btn_text_record.setOnClickListener(this);
        btn_image_record = findViewById(R.id.print_image_btn);
        btn_image_record.setOnClickListener(this);

        res = new ArrayList<>();

        String address = "#3B-11, Ist Floor, 3rd Block, VITC Export Bhavan, KIADB, Sub-Registrar, Office Building, 3rd Main,14th Cross, " +
                "4th, 2nd Stage, Peenya Industrial Area Phase IV, Peenya, Bengaluru, Karnataka 560058";
        String regex = "a-z~@#$%^&*:;<>.,/}{+";
        if (address.substring(0, 1).matches("[" + regex + "]+")){
            splchar = address.substring(0, 1);
        }
        splitString(address);
        barcode = getBitmap("1110101030468"+"123456.00");

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                checkPermissionsMandAbove();
            }
        }, 500);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.print_text_btn:
                break;

            case R.id.print_image_btn:
                int GPT_printer_height = 1900;
                es.submit(new TaskPrint(mCanvas, GPT_printer_height));
                break;
        }
    }

    private void enable_buttons(boolean value) {
//        btn_text_record.setEnabled(value);
        btn_image_record.setEnabled(value);
    }

    private void startService() {
        Intent intent = new Intent(MainActivity.this, BluetoothService.class);
        startService(intent);
    }

    private void stopService() {
        Intent intent = new Intent(MainActivity.this, BluetoothService.class);
        stopService(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mReceiver, new IntentFilter(BLUETOOTH_RESULT));
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra(PRINTER_MSG);
            switch (status) {
                case CONNECTED:
                    enable_buttons(true);
                    Toast.makeText(MainActivity.this, "Connected", Toast.LENGTH_SHORT).show();
                    break;

                case DISCONNECTED:
                    enable_buttons(false);
                    Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    @TargetApi(23)
    private void checkPermissionsMandAbove() {
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion >= 23) {
            if (!checkPermission()) {
                requestPermission();
            } else startService();
        } else startService();
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(MainActivity.this, new String[]
                {
                        ACCESS_FINE_LOCATION
                }, RequestPermissionCode);
    }

    private boolean checkPermission() {
        int FirstPermissionResult = ContextCompat.checkSelfPermission(getApplicationContext(), ACCESS_FINE_LOCATION);
        return FirstPermissionResult == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case RequestPermissionCode:
                if (grantResults.length > 0) {
                    boolean ReadLocationPermission = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    if (!ReadLocationPermission)
                        checkPermissionsMandAbove();
                    else startService();
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService();
        unregisterReceiver(mReceiver);
    }

    private class TaskPrint implements Runnable {
        Canvas canvas;
        int print_height;

        private TaskPrint(Canvas pos, int height) {
            this.canvas = pos;
            this.print_height = height;
        }

        @Override
        public void run() {
            final boolean bPrintResult = PrintTicket(canvas, print_height);
            final boolean bIsOpened = canvas.GetIO().IsOpened();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), bPrintResult ? getResources().getString(R.string.printsuccess) :
                            getResources().getString(R.string.printfailed), Toast.LENGTH_SHORT).show();
                    if (bIsOpened) {
                        yaxis = 0;
                    }
                }
            });
        }

        private boolean PrintTicket(Canvas canvas, int nPrintHeight) {
            boolean bPrintResult;
            Typeface tfNumber = Typeface.createFromAsset(getAssets(), "fonts/DroidSansMono.ttf");
            canvas.CanvasBegin(576, nPrintHeight);
            canvas.SetPrintDirection(0);

            int maxlength = 38;
            int small_font_height = 20;

            printtext(canvas, centeralign("HUBLI ELECTRICITY SUPPLY COMPANY LTD", maxlength), tfNumber, 25, 6);
            printtext(canvas, centeralign("RSD1.Belagavi", maxlength), tfNumber, 25, 6);
            printtext(canvas, centeralign("(" + 1110101 + ")", 38), tfNumber, 25, 60);
            printtext(canvas, space("RRNO/ಆರ್ ಆರ್ ಸಂಖ್ಯೆ", 14) + ":" + " " + "STB.45231", tfNumber, 30, 8);
            printtext(canvas, space("Account ID", 18) + ":" + " " + "1110101211498", tfNumber, 24, 6);
            printtext(canvas, space("Mtr.Rdr.Code", 18) + ":" + " " + "2", tfNumber, 24, 6);
            printtext(canvas, space(" ", 10) + "Name and Address/ಹೆಸರು ಮತ್ತು ವಿಳಾಸ", tfNumber, 20, 6);
            printtext(canvas, "Transvision Software", tfNumber, small_font_height, 4);
            if (res.size() == 1) {
                printtext(canvas, splchar+res.get(0), tfNumber, small_font_height, 4);
                printtext(canvas, " ", tfNumber, small_font_height, 4);
            }
            if (res.size() == 2) {
                printtext(canvas, splchar+res.get(0), tfNumber, small_font_height, 4);
                printtext(canvas, res.get(1), tfNumber, small_font_height, 4);
            }
            if (res.size() > 2) {
                printtext(canvas, splchar+res.get(0), tfNumber, small_font_height, 4);
                printtext(canvas, res.get(1), tfNumber, small_font_height, 4);
            }
            printtext(canvas, space("Tariff/ಸುಂಕ", 18) + ":" + " " + "LT-5A", tfNumber, 24, 6);
            printtext(canvas, space("Sanct Load", 18) + ":" + " HP:" + rightspacing("5.00", 5) +
                    String.format("%1s", " ") + "KW:" + rightspacing("0.00", 5), tfNumber, 24, 6);
            printtext(canvas, space("Billing Period", 18) + ":" + "01/05/2017" + "-" + "01/06/2017", tfNumber, 24, 6);
            printboldtext(canvas, space("Reading Date", 18) + ":" + " " + "01/06/2017", tfNumber, 24);
            printtext(canvas, space("BillNo", 14) + ":" + " " + "1110101030463" + "-" + "01/06/2017" , tfNumber, 24, 6);
            printtext(canvas, space("Meter SlNo.", 18) + ":" + " " + 0, tfNumber, 24, 6);
            printtext(canvas, space("Pres Rdg", 14) + ":" + " " + "35236 / NOR", tfNumber, 30, 8);
            printtext(canvas, space("Prev Rdg", 14) + ":" + " " + "34779 / NOR", tfNumber, 30, 8);
            printtext(canvas, space("Constant", 18) + ":" + " " + "1", tfNumber, 24, 6);
            printtext(canvas, space("Consumption/ಬಳಕೆ", 18) + ":" + " " + "457", tfNumber, 24, 6);
            printtext(canvas, space("Average", 18) + ":" + " " + "2", tfNumber, 24, 6);
            printtext(canvas, space("Recorded MD", 18) + ":" + " " + "1", tfNumber, 24, 6);
            printtext(canvas, space("Power Factor", 18) + ":" + " " + "0.85", tfNumber, 24, 6);
            printtext(canvas, centeralign("Fixed Charges", 38), tfNumber, 20, 4);
            printtext(canvas, String.format("%9s", rightspacing("10.0", 8)) + " " + "x" + String.format("%8s", rightspacing("40.00", 7)) + String.format("%18s", rightspacing("400.00", 12)), tfNumber, 24, 6);
            printtext(canvas, String.format("%9s", rightspacing("10.0", 8)) + " " + "x" + String.format("%8s", rightspacing("50.00", 7)) + String.format("%18s", rightspacing("500.00", 12)), tfNumber, 24, 6);
            printtext(canvas, centeralign("Energy Charges", 38), tfNumber, 20, 4);
            printtext(canvas, String.format("%9s", rightspacing("20.0", 8)) + " " + "x" + String.format("%8s", rightspacing("10.00", 7)) + String.format("%18s", rightspacing("200.00", 12)), tfNumber, 24, 6);
            printtext(canvas, String.format("%9s", rightspacing("200.0", 8)) + " " + "x" + String.format("%8s", rightspacing("10.00", 7)) + String.format("%18s", rightspacing("2000.00", 12)), tfNumber, 24, 6);
            printtext(canvas, String.format("%9s", rightspacing("2000.0", 8)) + " " + "x" + String.format("%8s", rightspacing("10.00", 7)) + String.format("%18s", rightspacing("20000.00", 12)), tfNumber, 24, 6);
            printtext(canvas, String.format("%9s", rightspacing("20.0", 8)) + " " + "x" + String.format("%8s", rightspacing("100.00", 7)) + String.format("%18s", rightspacing("2000.00", 12)), tfNumber, 24, 6);
            printtext(canvas, space("FEC", 7) + ":" + " " + String.format("%7s", rightspacing("457", 7)) + " " + "x" + String.format("%7s", rightspacing("0.06", 7)) + String.format("%12s", rightspacing("275.25", 11)), tfNumber, 24, 6);
            printtext(canvas, space("Rebates/TOD", 18) + ":" + " " + String.format("%17s", rightspacing("10.00", 12)), tfNumber, 24, 6);
            printtext(canvas, space("PF Penalty", 18) + ":" + " " + String.format("%17s", rightspacing("105.00", 12)), tfNumber, 24, 6);
            printtext(canvas, space("MD Penalty", 18) + ":" + " " + String.format("%17s", rightspacing("205.00", 12)), tfNumber, 24, 6);
            printtext(canvas, space("Interest", 18) + ":" + " " + String.format("%17s", rightspacing("150.00", 12)), tfNumber, 24, 6);
            printtext(canvas, space("Others", 18) + ":" + " " + String.format("%17s", rightspacing("100.00", 12)), tfNumber, 24, 6);
            printtext(canvas, space("Tax", 18) + ":" + " " + String.format("%17s", rightspacing("125.00", 12)), tfNumber, 24, 6);
            printtext(canvas, space("Cur Bill Amt", 18) + ":" + " " + String.format("%17s", rightspacing("12546.00", 12)), tfNumber, 24, 6);
            printtext(canvas, space("Arrears", 18) + ":" + " " + String.format("%17s", rightspacing("1234.00", 12)), tfNumber, 24, 6);
            printtext(canvas, space("Credits & Adj", 18) + ":" + " " + String.format("%17s", rightspacing("1000.00", 12)), tfNumber, 24, 6);
            printtext(canvas, space("GOK Subsidy", 18) + ":" + " " + String.format("%17s", rightspacing("0.00", 12)), tfNumber, 24, 6);
            printboldtext(canvas, space("Net Amt Due", 12) + ":" + " " + String.format("%11s", rightspacing("123456.00", 12)), tfNumber, 35);
            printtext(canvas, space("Due Date", 18) + ":" + " " + String.format("%17s", rightspacing("14/07/2017", 12)), tfNumber, 24, 6);
            printtext(canvas, space("Printed On", 16) + ":" + " " + String.format("%19s", rightspacing(currentDateandTime(), 16)), tfNumber, 24, 6);
            canvas.DrawBitmap(barcode, 0, yaxis, 0);
            canvas.CanvasEnd();
            canvas.CanvasPrint(1, 0);

            bPrintResult = canvas.GetIO().IsOpened();
            return bPrintResult;
        }

        private void printtext(Canvas canvas, String text, Typeface tfNumber, float textsize, float axis) {
            yaxis++;
            canvas.DrawText(text + "\r\n", 0, yaxis, 0, tfNumber, textsize, Canvas.DIRECTION_LEFT_TO_RIGHT);
            if (textsize == 20) {
                yaxis = yaxis + textsize + 8;
            } else yaxis = yaxis + textsize + 6;
            yaxis = yaxis + axis;
        }

        private void printboldtext(Canvas canvas, String text, Typeface tfNumber, float textsize) {
            yaxis++;
            canvas.DrawText(text + "\r\n", 0, yaxis, 0, tfNumber, textsize, Canvas.FONTSTYLE_BOLD);
            if (textsize == 20) {
                yaxis = yaxis + textsize + 8;
            } else yaxis = yaxis + textsize + 6;
            yaxis = yaxis + (float) 6;
        }

        private String rightspacing(String s1, int len) {
            StringBuilder s1Builder = new StringBuilder(s1);
            for (int i = 0; i < len - s1Builder.length(); i++) {
                s1Builder.insert(0, " ");
            }
            s1 = s1Builder.toString();
            return (s1);
        }

        private String centeralign(String text, int width) {
            int count = text.length();
            int value = width - count;
            int append = (value / 2);
            return space(" ", append) + text;
        }

        private String space(String s, int length) {
            int temp;
            StringBuilder spaces = new StringBuilder();
            temp = length - s.length();
            for (int i = 0; i < temp; i++) {
                spaces.append(" ");
            }
            return (s + spaces);
        }

        private String currentDateandTime() {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            return sdf.format(new Date());
        }
    }

    private void splitString(String msg) {
        res.clear();
        Pattern p = Pattern.compile("\\b.{0," + (47 -1) + "}\\b\\W?");
        Matcher m = p.matcher(msg);
        while(m.find()) {
            res.add(m.group().trim());
        }
    }

    private Bitmap getBitmap(String barcode) {
        Bitmap barcodeBitmap = null;
        BarcodeFormat barcodeFormat = convertToZXingFormat();
        try {
            barcodeBitmap = encodeAsBitmap(barcode, barcodeFormat);
        } catch (WriterException e) {
            e.printStackTrace();
        }
        return barcodeBitmap;
    }

    private BarcodeFormat convertToZXingFormat() {
        switch (1) {
            case 8:
                return BarcodeFormat.CODABAR;
            case 1:
                return BarcodeFormat.CODE_128;
            case 2:
                return BarcodeFormat.CODE_39;
            case 4:
                return BarcodeFormat.CODE_93;
            case 32:
                return BarcodeFormat.EAN_13;
            case 64:
                return BarcodeFormat.EAN_8;
            case 128:
                return BarcodeFormat.ITF;
            case 512:
                return BarcodeFormat.UPC_A;
            case 1024:
                return BarcodeFormat.UPC_E;
            //default 128?
            default:
                return BarcodeFormat.CODE_128;
        }
    }


    /**************************************************************
     * getting from com.google.zxing.client.android.encode.QRCodeEncoder
     *
     * See the sites below
     * http://code.google.com/p/zxing/
     * http://code.google.com/p/zxing/source/browse/trunk/android/src/com/google/zxing/client/android/encode/EncodeActivity.java
     * http://code.google.com/p/zxing/source/browse/trunk/android/src/com/google/zxing/client/android/encode/QRCodeEncoder.java
     */

    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;

    private Bitmap encodeAsBitmap(String contents, BarcodeFormat format) throws WriterException {
        if (contents == null) {
            return null;
        }
        Map<EncodeHintType, Object> hints = null;
        String encoding = guessAppropriateEncoding(contents);
        if (encoding != null) {
            hints = new EnumMap(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, encoding);
        }
        MultiFormatWriter writer = new MultiFormatWriter();
        BitMatrix result;
        try {
            result = writer.encode(contents, format, 450, 45, hints);
        } catch (IllegalArgumentException iae) {
            // Unsupported format
            return null;
        }
        int width = result.getWidth();
        int height = result.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height,
                Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private String guessAppropriateEncoding(CharSequence contents) {
        // Very crude at the moment
        for (int i = 0; i < contents.length(); i++) {
            if (contents.charAt(i) > 0xFF) {
                return "UTF-8";
            }
        }
        return null;
    }
}
