package com.transvision.test_gpt_printer;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Layout;
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
    private float xaxis = 0;
    private String splchar = "";
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
        if (address.substring(0, 1).matches("[" + regex + "]+")) {
            splchar = address.substring(0, 1);
        }
        splitString(address);
        barcode = getBitmap("1110101030468" + "123456.00");

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
                int GPT_printer_height = 1500;
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

    //******************************************************** TaskPrint **********************************************************************
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

            printtext(canvas, "ಮಾ.ಓ.ಸಂಕೇತ/Mtr.Rdr.Code", "54003905", tfNumber, 20, 4);
            printtext(canvas, "ಉಪ ವಿಭಾಗ/Sub Division", "54003801", tfNumber, 20, 4);
            printtext(canvas, "ಖಾತೆ ಸಂಖ್ಯೆ/Account ID", "9913164549", tfNumber, 20, 4);
            printtext_center(canvas, "ಹೆಸರು ಮತ್ತು ವಿಳಾಸ/Name and Address", tfNumber, 20, 4);
            printtext(canvas, "ಜಕಾತಿ/Tariff", "5LT6B-M", tfNumber, 20, 4);
            printtext(canvas, "ಮಂ.ಪ್ರಮಾಣ/Sanct Load", "HP: 3  KW 2", tfNumber, 20, 4);
            printtext(canvas, "ಬಿಲ್ಲಿಂಗ್ ಅವಧಿ/Billing Period", "01/07/2018" + "-" + "01/08/2018", tfNumber, 20, 4);
            printtext(canvas, "ರೀಡಿಂಗ ದಿನಾಂಕ/Reading Date", "01/08/2018", tfNumber, 20, 4);
            printtext(canvas, "ಬಿಲ್ ಸಂಖ್ಯೆ/Bill No", "991316454908201801", tfNumber, 20, 4);
            printtext(canvas, "ಮೀಟರ್ ಸಂಖ್ಯೆ/Meter SlNo", "500010281098" + 0, tfNumber, 20, 4);
            printtext(canvas, "ಇಂದಿನ ಮಾಪನ/Pres Rdg", "658 / NOR", tfNumber, 20, 4);
            printtext(canvas, "ಹಿಂದಿನ ಮಾಪನ/Prev Rdg", "600 / NOR", tfNumber, 20, 4);
            printtext(canvas, "ಮಾಪನ ಸ್ಥಿರಾಂಕ/Constant", rightspacing("1", 5), tfNumber, 20, 4);
            printtext(canvas, "ಬಳಕೆ/Consumption", rightspacing("58", 5), tfNumber, 20, 4);
            printtext(canvas, "ಸರಾಸರಿ/Average", rightspacing("51", 5), tfNumber, 20, 4);
            printtext(canvas, "ದಾಖಲಿತ ಬೇಡಿಕೆ/Recorded MD", rightspacing("10", 5), tfNumber, 20, 4);

            printtext(canvas, "ಪವರ ಫ್ಯಾಕ್ಟರ/Power Factor", rightspacing("0.85", 14), tfNumber, 20, 4);
          /*  printtext_center(canvas, "ನಿಗದಿತ ಶುಲ್ಕ/Fixed Charges",  tfNumber, 20, 4);

           // printtext(canvas, "3.0",  "x" + String.format("%8s", rightspacing("80.00", 7)) + String.format("%18s", rightspacing(" 240.00", 12)), tfNumber, 24, 6);

            printtext_center(canvas, "ವಿದ್ಯುತ್ ಶುಲ್ಕ/Energy Charges" , tfNumber, 20, 6);
            printtext(canvas, "58.0",  "x" + "6.05" + "350.90", tfNumber, 20, 6);*/


            printtext(canvas, "ಎಫ್.ಎ.ಸಿ/FAC", rightspacing("1258.0", 14), tfNumber, 20, 6);
            printtext(canvas, "ರಿಯಾಯಿತಿ/Rebates/TOD", rightspacing("10.00", 14), tfNumber, 20, 6);
            printtext(canvas, "ಪಿ.ಎಫ್ ದಂಡ/PF Penalty", rightspacing("200.00", 14), tfNumber, 20, 6);
            printtext(canvas, "ಎಂ.ಡಿ.ದಂಡ/MD Penalty", rightspacing("0.00", 14), tfNumber, 20, 6);
            printtext(canvas, "ಬಡ್ಡಿ/Interest @1%", rightspacing("3.64", 14), tfNumber, 20, 6);
            printtext(canvas, "ಇತರೆ/Others", rightspacing("0.00", 14), tfNumber, 20, 6);
            printtext(canvas, "ತೆರಿಗೆ/Tax @9%", rightspacing("25.47", 14), tfNumber, 20, 6);
            printtext(canvas, "ಒಟ್ಟು ಬಿಲ್ ಮೊತ್ತ/Cur Bill Amt", rightspacing("620.01", 14), tfNumber, 20, 6);

            printtext(canvas, "ಬಾಕಿ/Arrears", rightspacing("1258.0", 14), tfNumber, 20, 6);
            printtext(canvas, "DL ಬಿಲ್/DL Bill", rightspacing("10.00", 14), tfNumber, 20, 6);
            printtext(canvas, "ಜಮಾ/Credits & Adj", rightspacing("320.01", 14), tfNumber, 20, 6);
            printtext(canvas, "ಐ.ಒ.ಡಿ/IOD", rightspacing("520.01", 14), tfNumber, 20, 6);
            printtext(canvas, "ಸರ್ಕಾರದ ಸಹಾಯಧನ/GOK Subsidy", rightspacing("3.64", 14), tfNumber, 20, 6);
            printtext(canvas, "ಪಾವತಿಸಬೇಕಾದ ಮೊತ್ತ/Net Amt Due", rightspacing("0.00", 14), tfNumber, 20, 6);
            printtext(canvas, "ತೆರಿಗೆ/Tax @9%", rightspacing("25.47", 14), tfNumber, 20, 6);

            //printtext(canvas, "ಒಟ್ಟು ಬಿಲ್ ಮೊತ್ತ/Cur Bill Amt", rightspacing("1258.0", 14), tfNumber, 20, 6);
            printtext(canvas, "ಬಾಕಿ/Arrears", rightspacing("10.00", 14), tfNumber, 20, 6);
            printtext(canvas, "DL ಬಿಲ್/DL Bill", rightspacing("320.01", 14), tfNumber, 20, 6);
            printtext(canvas, "ಜಮಾ/Credits & Adj", rightspacing("520.01", 14), tfNumber, 20, 6);
            printtext(canvas, "ಐ.ಒ.ಡಿ/IOD", rightspacing("3.64", 14), tfNumber, 20, 6);
           // printtext(canvas, "ಪಸರ್ಕಾರದ ಸಹಾಯಧನ/GOK Subsidy", rightspacing("0.00", 14), tfNumber, 20, 6);
            //printtext(canvas, "ಪಾವತಿಸಬೇಕಾದ ಮೊತ್ತ/Net Amt Due", rightspacing("2500.47", 14), tfNumber, 20, 6);

           // printtext(canvas, "ಪಾವತಿ ಕೊನೆ ದಿನಾಂಕ/Due Date", rightspacing("15/08/2018", 14), tfNumber, 20, 6);
            printtext(canvas, "ಬಾಕಿ/Arrears", rightspacing("10.00", 14), tfNumber, 20, 6);
            //printtext(canvas, "ಬಿಲ್ ದಿನಾಂಕ/Billed On", rightspacing(currentDateandTime(), 14), tfNumber, 20, 6);
            //printtext(canvas, "ಮಾ.ಓ.ಸಂಕೇತ/Mtr.Rdr.Code", rightspacing("54003818 Kenchappa", 14), tfNumber, 20, 6);

            canvas.DrawBitmap(barcode, 0, yaxis, 0);
            canvas.CanvasEnd();
            canvas.CanvasPrint(1, 0);
            bPrintResult = canvas.GetIO().IsOpened();
            return bPrintResult;

        }

        private Bitmap Print_unicode(String text) {
            Bitmap myBitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_4444);
            android.graphics.Canvas myCanvas = new android.graphics.Canvas(myBitmap);
            Paint paint = new Paint();
            Typeface clock = Typeface.createFromAsset(getAssets(), "DroidSansMono.ttf");
           /* paint.setAntiAlias(true);
            paint.setSubpixelText(true);*/
            paint.setTypeface(clock);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.BLACK);
            paint.setTextSize(15);
            paint.setTextAlign(Paint.Align.CENTER);
            myCanvas.drawText(text, 0, 180, paint);
            return myBitmap;
        }

        /* private boolean PrintTicket(Canvas canvas, int nPrintHeight) {
            boolean bPrintResult;
            Typeface tfNumber = Typeface.createFromAsset(getAssets(), "fonts/DroidSansMono.ttf");
            canvas.CanvasBegin(576, nPrintHeight);
            canvas.SetPrintDirection(0);

            int maxlength = 38;
            int small_font_height = 20;

            printboldtext(canvas, space("HUBLI ELECTRICITY SUPPLY COMPANY LTD", maxlength), tfNumber, 25);
            //printboldtext(canvas, space("", maxlength), tfNumber, 24);
            printtext(canvas, "ಉಪ ವಿಭಾಗ/Sub Division",  "540038", tfNumber, 22, 6);
            //printtext(canvas, "ಆರ್.ಆರ್.ಸಂಖ್ಯೆ/RRNO", "BSTL43242", tfNumber, 22, 6);
            printtext(canvas, "ಖಾತೆ ಸಂಖ್ಯೆ/Account ID",  "9913164549", tfNumber, 22, 6);
            printtext1(canvas,  "ಹೆಸರು ಮತ್ತು ವಿಳಾಸ/Name and Address", tfNumber, 22, 6);

            printtext1(canvas, "THE COMMISSIONER CITY CORPORATION BELGAVI",  tfNumber, 24, 6);
            printtext1(canvas, "DHANSHREE GARDEN FOURTH CROSS BHAGYANAGAR", tfNumber, 24, 6);


            printtext(canvas, "ಜಕಾತಿ/Tariff", "5LT6B-M", tfNumber, 22, 6);
            printtext(canvas, "ಮಂ.ಪ್ರಮಾಣ/Sanct Load", " HP:" + rightspacing("0", 6) +
                    String.format("%1s", " ") + "KW:" + rightspacing("3", 5), tfNumber, 22, 6);
            printtext(canvas, "ಬಿಲ್ಲಿಂಗ್ ಅವಧಿ/Billing Period", "01/07/2018" + "-" + "01/08/2018", tfNumber, 20, 4);
            printtext(canvas, "ರೀಡಿಂಗ ದಿನಾಂಕ/Reading Date", "01/08/2018", tfNumber, 22, 6);
            printtext(canvas, "ಬಿಲ್ ಸಂಖ್ಯೆ/Bill No",   "991316454908201801", tfNumber, 22, 6);
            printtext(canvas, "ಮೀಟರ್ ಸಂಖ್ಯೆ/Meter SlNo",  "500010281098" + 0, tfNumber, 22, 6);
            printtext(canvas, "ಇಂದಿನ ಮಾಪನ/Pres Rdg",  "658 / NOR", tfNumber, 22, 6);
            printtext(canvas, "ಹಿಂದಿನ ಮಾಪನ/Prev Rdg",  "600 / NOR", tfNumber, 22, 6);
            printtext(canvas, "ಮಾಪನ ಸ್ಥಿರಾಂಕ/Constant",  "1", tfNumber, 22, 6);
            printtext(canvas, "ಬಳಕೆ/Consumption",  "58", tfNumber, 22, 6);
            printtext(canvas, "ಸರಾಸರಿ/Average", "51", tfNumber, 22, 6);
            printtext(canvas, "ದಾಖಲಿತ ಬೇಡಿಕೆ/Recorded MD",  "1", tfNumber, 24, 6);
            printtext(canvas, "ಪವರ ಫ್ಯಾಕ್ಟರ/Power Factor",  "0.85", tfNumber, 24, 6);
            printboldtext(canvas, space("", maxlength), tfNumber, 20);
            printtext1(canvas, "ನಿಗದಿತ ಶುಲ್ಕ/Fixed Charges",  tfNumber, 24, 6);

            printtext(canvas, "3.0",  "x" + String.format("%8s", rightspacing("80.00", 7)) + String.format("%18s", rightspacing("     240.00", 12)), tfNumber, 24, 6);
           // printtext(canvas, String.format("%9s", rightspacing("10.0", 8)) + " " + "x" + String.format("%8s", rightspacing("50.00", 7)) + String.format("%18s", rightspacing("500.00", 12)), tfNumber, 24, 6);
            printboldtext(canvas, space("", maxlength), tfNumber, 20);
            printtext(canvas, centeralign("ವಿದ್ಯುತ್ ಶುಲ್ಕ/Energy Charges", 38), tfNumber, 24, 6);
            printtext(canvas, String.format("%9s", rightspacing("58.0", 8)) + " " + "x" + String.format("%8s", rightspacing("6.05", 7)) + String.format("%18s", rightspacing("     350.90", 12)), tfNumber, 24, 6);

            printtext(canvas, String.format("%9s", rightspacing("200.0", 8)) + " " + "x" + String.format("%8s", rightspacing("10.00", 7)) + String.format("%18s", rightspacing("2000.00", 12)), tfNumber, 24, 6);
            printtext(canvas, String.format("%9s", rightspacing("2000.0", 8)) + " " + "x" + String.format("%8s", rightspacing("10.00", 7)) + String.format("%18s", rightspacing("20000.00", 12)), tfNumber, 24, 6);
            printtext(canvas, String.format("%9s", rightspacing("20.0", 8)) + " " + "x" + String.format("%8s", rightspacing("100.00", 7)) + String.format("%18s", rightspacing("2000.00", 12)), tfNumber, 24, 6);
           // printboldtext(canvas, space("", maxlength), tfNumber, 20);
            printtext(canvas, space("ಎಫ್.ಎ.ಸಿ/FAC", 10) + ":" + " " + String.format("%10s", rightspacing("58.0", 7)) + " " + "x" + String.format
                    ("%7s", rightspacing("0.00", 7)) + String.format("%6s", rightspacing("0.00", 6)), tfNumber, 22, 6);
            printtext(canvas, space("ರಿಯಾಯಿತಿ/Rebates/TOD", 10) + "(-):" + " " + String.format("%10s", rightspacing("           0.00", 10)), tfNumber, 22, 6);
            printtext(canvas, space("ಪಿ.ಎಫ್ ದಂಡ/PF Penalty", 10) + ":" + " " + String.format("%10s", rightspacing("             0.00", 10)), tfNumber, 22, 6);
            printtext(canvas, space("ಎಂ.ಡಿ.ದಂಡ/MD Penalty", 10) + ":" + " " + String.format("%10s", rightspacing("              0.00", 10)), tfNumber, 22, 6);
            printtext(canvas, space("ಬಡ್ಡಿ/Interest @1%", 10) + ":" + " " + String.format("%10s", rightspacing("                  3.64", 10)), tfNumber, 22, 6);
            printtext(canvas, space("ಇತರೆ/Others", 10) + ":" + " " + String.format("%10s", rightspacing("                       0.00", 10)), tfNumber, 22, 6);
            printtext(canvas, space("ತೆರಿಗೆ/Tax @9%", 10) + ":" + " " + String.format("%10s", rightspacing("                      25.47", 10)), tfNumber, 22, 6);
            printtext(canvas, space("ಒಟ್ಟು ಬಿಲ್ ಮೊತ್ತ/Cur Bill Amt", 10) + ":" + " " + String.format("%10s", rightspacing("        620.01", 10)), tfNumber, 22, 6);
            printtext(canvas, space("ಬಾಕಿ/Arrears", 10) + ":" + " " + String.format("%10s", rightspacing("                       1117.00", 10)), tfNumber, 22, 6);
            printtext(canvas, space("DL ಬಿಲ್/DL Bill", 10) + "(-):" + " " + String.format("%10s", rightspacing("                 0.00", 10)), tfNumber, 22, 6);

            printtext(canvas, space("ಜಮಾ/Credits & Adj", 10) + ":" + " " + String.format("%10s", rightspacing("               -312.00", 10)), tfNumber, 22, 6);
           // printtext(canvas, space("ಐ.ಒ.ಡಿ/IOD", 10) + ":" + " " + String.format("%10s", rightspacing("1000.00", 10)), tfNumber, 22, 6);
            printtext(canvas, space("ಸರ್ಕಾರದ ಸಹಾಯಧನ/GOK Subsidy", 10) + "(-):" + " " + String.format("%6s", rightspacing("0.00", 10)), tfNumber, 22, 6);
            printboldtext(canvas, space("ಪಾವತಿಸಬೇಕಾದ ಮೊತ್ತ/Net Amt Due", 10) + ":" + " " + String.format("%6s", rightspacing("1425.00", 10)), tfNumber, 24);
            printtext(canvas, space("ಪಾವತಿ ಕೊನೆ ದಿನಾಂಕ/Due Date", 10) + ":" + " " + String.format("%10s", rightspacing("15/08/2018", 10)), tfNumber, 22, 6);
            printtext(canvas, space("ಬಿಲ್ ದಿನಾಂಕ/Billed On", 10) + ":" + " " + String.format("%10s", rightspacing(currentDateandTime(), 10)), tfNumber, 22, 6);
            printboldtext(canvas, space("", maxlength), tfNumber, 20);
            printtext(canvas, space("ಮಾ.ಓ.ಸಂಕೇತ/Mtr.Rdr.Code", 12) + ":" + " " + "54003818 Kenchappa Betageri", tfNumber, 24, 6);

            canvas.DrawBitmap(barcode, 0, yaxis, 0);
            canvas.CanvasEnd();
            canvas.CanvasPrint(1, 0);

            bPrintResult = canvas.GetIO().IsOpened();
            return bPrintResult;
        }*/

        private void printtext(Canvas canvas, String text, String text1, Typeface tfNumber, float textsize, float axis) {
            yaxis++;
            canvas.DrawText(text, 0, yaxis, 0, tfNumber, textsize, Canvas.DIRECTION_LEFT_TO_RIGHT);
            canvas.DrawText(text1 + "\r\n", 350, yaxis, 0, tfNumber, textsize, Canvas.DIRECTION_LEFT_TO_RIGHT);
            if (textsize == 20) {
                yaxis = yaxis + textsize + 8;
            } else yaxis = yaxis + textsize + 6;
            yaxis = yaxis + axis;
        }

        private void printtext1(Canvas canvas, String text, Typeface tfNumber, float textsize, float axis) {
            yaxis++;
            canvas.DrawText(text + "\r\n", 0, yaxis, 0, tfNumber, textsize, Canvas.DIRECTION_LEFT_TO_RIGHT);
            if (textsize == 20) {
                yaxis = yaxis + textsize + 8;
            } else yaxis = yaxis + textsize + 6;
            yaxis = yaxis + axis;
        }

        private void printtext_center(Canvas canvas, String text, Typeface tfNumber, float textsize, float axis) {
            yaxis++;
            canvas.DrawText(text + "\r\n", 120, yaxis, 0, tfNumber, textsize, Canvas.DIRECTION_LEFT_TO_RIGHT);
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
            int i;
            StringBuilder s1Builder = new StringBuilder(s1);
            for (i = 0; i < len - s1Builder.length(); i++) {
            }
            s1Builder.insert(0, String.format("%" + i + "s", ""));
            s1 = s1Builder.toString();
            return (s1);
        }

        private String rightspacing2(String s1, int len) {
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

        private String centeralign2(String text, int width) {
            int count = text.length();
            int value = width - count;
            int append = (value / 2);
            return space1(" ", append) + text;
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

        private String space1(String s, int length) {
            int temp;
            StringBuilder spaces = new StringBuilder();
            temp = length - s.length();
            for (int i = 0; i < temp; i++) {
                spaces.insert(0, String.format("%" + i + "s", ""));
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
        Pattern p = Pattern.compile("\\b.{0," + (47 - 1) + "}\\b\\W?");
        Matcher m = p.matcher(msg);
        while (m.find()) {
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
            default:
                return BarcodeFormat.CODE_128;
        }
    }


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

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
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
