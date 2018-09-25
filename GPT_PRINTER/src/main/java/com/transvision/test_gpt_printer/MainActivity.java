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
    private String rep_address_1="",rep_address_2="";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btn_text_record = findViewById(R.id.print_text_btn);
        btn_text_record.setOnClickListener(this);
        btn_image_record = findViewById(R.id.print_image_btn);
        btn_image_record.setOnClickListener(this);

        res = new ArrayList<>();

        String address = "#3B-11, Ist Floor, 3rd Block, VITC Export Bhavan, KIADB, Sub-Registrar, Office Building, 3rd Main,14th Cross";
        String regex = "a-z~@#$%^&*:;<>.,/}{+";
        if (address.substring(0, 1).matches("[" + regex + "]+")) {
            splchar = address.substring(0, 1);
        }
        splitString(address,30,res);
        if (res.size()>0)
        {
            rep_address_1 = "  "+res.get(0);
            if (res.size() > 1) {
                rep_address_2 = "  "+res.get(1);
            }
        }
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
                int GPT_printer_height = 1800;
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
            int maxlength = 38;
            printboldtext(canvas, space("HUBLI ELECTRICITY SUPPLY COMPANY LTD", maxlength), tfNumber, 25);
            printtext_center(canvas, "", tfNumber, 20, 4);

            printtext(canvas, "ಉಪ ವಿಭಾಗ/Sub Division", centeralign2(":", 0), "540038", tfNumber, 20, 4);
            printtext(canvas, "ಆರ್.ಆರ್.ಸಂಖ್ಯೆ/RRNO", centeralign2(":", 0), "IP57.228", tfNumber, 20, 4);
            printtext(canvas, "ಖಾತೆ ಸಂಖ್ಯೆ/Account ID", centeralign2(":", 0), "9913164549", tfNumber, 20, 4);

            printtext_center(canvas, centeralign2("ಹೆಸರು ಮತ್ತು ವಿಳಾಸ/Name and Address", 10), tfNumber, 20, 6);
            printtext_center(canvas, centeralign2(rep_address_1, 10), tfNumber, 20, 6);
            printtext_center(canvas, centeralign2(rep_address_2, 10), tfNumber, 20, 6);
            printtext(canvas, "ಜಕಾತಿ/Tariff", centeralign2(":", 0), "5LT6B-M", tfNumber, 20, 4);
            printtext(canvas, "ಮಂ.ಪ್ರಮಾಣ/Sanct Load", centeralign2(":", 0), "HP: 3  KW 2", tfNumber, 20, 4);
            printtext(canvas, "Billing Period", centeralign2(":", 0), "01/07/2018" + "-" + "01/08/2018", tfNumber, 20, 4);
            printtext(canvas, "ರೀಡಿಂಗ ದಿನಾಂಕ/Reading Date", centeralign2(":", 0), "01/08/2018", tfNumber, 20, 4);
            printtext(canvas, "ಬಿಲ್ ಸಂಖ್ಯೆ/Bill No", centeralign2(":", 0), "991316454908201801", tfNumber, 20, 4);
            printtext(canvas, "ಮೀಟರ್ ಸಂಖ್ಯೆ/Meter SlNo", centeralign2(":", 0), "500010281098" + 0, tfNumber, 20, 4);
            printtext(canvas, "ಇಂದಿನ ಮಾಪನ/Pres Rdg", centeralign2(":", 0), "658 / NOR", tfNumber, 20, 4);
            printtext(canvas, "ಹಿಂದಿನ ಮಾಪನ/Prev Rdg", centeralign2(":", 0), "600 / NOR", tfNumber, 20, 4);
            printtext(canvas, "ಮಾಪನ ಸ್ಥಿರಾಂಕ/Constant", centeralign2(":", 0), rightspacing("1", 5), tfNumber, 20, 4);
            printtext(canvas, "ಬಳಕೆ/Consumption", centeralign2(":", 0), rightspacing("58", 5), tfNumber, 20, 4);
            printtext(canvas, "ಸರಾಸರಿ/Average", centeralign2(":", 0), rightspacing("51", 5), tfNumber, 20, 4);
            printtext(canvas, "ದಾಖಲಿತ ಬೇಡಿಕೆ/Recorded MD", centeralign2(":", 0), rightspacing("10", 5), tfNumber, 20, 4);
            printtext(canvas, "ಪವರ ಫ್ಯಾಕ್ಟರ/Power Factor", centeralign2(":", 0), rightspacing("0.85", 5), tfNumber, 20, 4);
            printtext_center(canvas, "", tfNumber, 20, 4);

            printtext_center(canvas, centeralign2("ನಿಗದಿತ ಶುಲ್ಕ/Fixed Charges", 10), tfNumber, 20, 6);
            printtext_center(canvas, "", tfNumber, 20, 4);
            printtext_center(canvas, centeralign2("3.0 x 60.00", 10) + rightspacing("180.00", 22), tfNumber, 20, 6);
            printtext_center(canvas, centeralign2("2.0 x 80.00", 10) + rightspacing("160.00", 22), tfNumber, 20, 6);
            printtext_center(canvas, "", tfNumber, 20, 4);

            printtext_center(canvas, centeralign2("ವಿದ್ಯುತ್ ಶುಲ್ಕ/Energy Charges", 10), tfNumber, 20, 6);
            printtext_center(canvas, "", tfNumber, 20, 4);
            printtext_center(canvas, centeralign2("2.0 x 40.00", 10) + rightspacing("80.00", 22), tfNumber, 20, 6);
            printtext_center(canvas, centeralign2("2.0 x 50.00", 10) + rightspacing("100.00", 22), tfNumber, 20, 6);
            printtext_center(canvas, "", tfNumber, 20, 4);

            printtext2(canvas, "ಎಫ್.ಎ.ಸಿ/FAC", centeralign2(":", 0), rightspacing("1258.00", 14), tfNumber, 20, 6);
            printtext2(canvas, "ರಿಯಾಯಿತಿ/Rebates/TOD", centeralign2(":", 0), rightspacing("10.00", 14), tfNumber, 20, 6);
            printtext2(canvas, "ಪಿ.ಎಫ್ ದಂಡ/PF Penalty", centeralign2(":", 0), rightspacing("200.00", 14), tfNumber, 20, 6);
            printtext2(canvas, "ಎಂ.ಡಿ.ದಂಡ/MD Penalty", centeralign2(":", 0), rightspacing("0.00", 14), tfNumber, 20, 6);
            printtext2(canvas, "ಬಡ್ಡಿ/Interest @1%", centeralign2(":", 0), rightspacing("3.64", 14), tfNumber, 20, 6);
            printtext2(canvas, "ಇತರೆ/Others", centeralign2(":", 0), rightspacing("0.00", 14), tfNumber, 20, 6);
            printtext2(canvas, "ತೆರಿಗೆ/Tax @9%", centeralign2(":", 0), rightspacing("25.47", 14), tfNumber, 20, 6);
            printtext2(canvas, "ಒಟ್ಟು ಬಿಲ್ ಮೊತ್ತ/Cur Bill Amt", centeralign2(":", 0), rightspacing("620.01", 14), tfNumber, 20, 6);


            printtext2(canvas, "ಬಾಕಿ/Arrears", centeralign2(":", 0), rightspacing("1258.00", 14), tfNumber, 20, 6);
            printtext2(canvas, "ಜಮಾ/Credits & Adj", centeralign2(":", 0), rightspacing("320.01", 14), tfNumber, 20, 6);
            printtext2(canvas, "ಐ.ಒ.ಡಿ/IOD", centeralign2(":", 0), rightspacing("520.01", 14), tfNumber, 20, 6);
            printtext2(canvas, "ಸರ್ಕಾರದ ಸಹಾಯಧನ/GOK Subsidy", centeralign2(":", 0), rightspacing("3.64", 14), tfNumber, 20, 6);
            printboldtext1(canvas, "Net Amt Due", centeralign2(":", 0), rightspacing("978950.00", 11), tfNumber, 25, 6);


            printtext2(canvas, "ಪಾವತಿ ಕೊನೆ ದಿನಾಂಕ/Due Date", centeralign2(":", 0), space1("15/08/2018", 5), tfNumber, 20, 6);
            printtext2(canvas, "ಬಿಲ್ ದಿನಾಂಕ/Billed On", centeralign2(":", 0), space3(currentDateandTime(), 3), tfNumber, 20, 6);
            printtext2(canvas, "ಮಾ.ಓ.ಸಂಕೇತ/Mtr.Rdr.Code", centeralign2(":", 0), space3("54003818", 4), tfNumber, 20, 6);

            canvas.DrawBitmap(barcode, 0, yaxis, 0);
            canvas.CanvasEnd();
            canvas.CanvasPrint(1, 0);
            bPrintResult = canvas.GetIO().IsOpened();
            return bPrintResult;

        }


        private void printtext(Canvas canvas, String text, String text1, String text2, Typeface tfNumber, float textsize, float axis) {
            yaxis++;
            canvas.DrawText(text, 0, yaxis, 0, tfNumber, textsize, Canvas.DIRECTION_LEFT_TO_RIGHT);
            canvas.DrawText(text1, 310, yaxis, 0, tfNumber, textsize, Canvas.DIRECTION_LEFT_TO_RIGHT);
            canvas.DrawText(text2 + "\r\n", 325, yaxis, 0, tfNumber, textsize, Canvas.DIRECTION_LEFT_TO_RIGHT);
            if (textsize == 20) {
                yaxis = yaxis + textsize + 8;
            } else yaxis = yaxis + textsize + 6;
            yaxis = yaxis + axis;
        }

        private void printtext2(Canvas canvas, String text, String text2, String text3, Typeface tfNumber, float textsize, float axis) {
            yaxis++;
            canvas.DrawText(text, 0, yaxis, 0, tfNumber, textsize, Canvas.DIRECTION_LEFT_TO_RIGHT);
            canvas.DrawText(text2, 310, yaxis, 0, tfNumber, textsize, Canvas.DIRECTION_LEFT_TO_RIGHT);
            canvas.DrawText(text3 + "\r\n", 350, yaxis, 0, tfNumber, textsize, Canvas.DIRECTION_LEFT_TO_RIGHT);
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

        private void printboldtext1(Canvas canvas, String text, String text1, String text2, Typeface tfNumber, float textsize, float axis) {
            yaxis++;
            canvas.DrawText(text, 0, yaxis, 0, tfNumber, textsize, Canvas.FONTSTYLE_BOLD);
            canvas.DrawText(text1, 0, yaxis, 0, tfNumber, textsize, Canvas.FONTSTYLE_BOLD);
            canvas.DrawText(text2 + "\r\n", 350, yaxis, 0, tfNumber, textsize, Canvas.FONTSTYLE_BOLD);
            if (textsize == 20) {
                yaxis = yaxis + textsize + 8;
            } else yaxis = yaxis + textsize + 6;
            yaxis = yaxis + axis;
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
            //return space1(" ", append) + text;
            return space1(String.format("%s", ""), append) + text;
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

        private String space3(String s, int length) {
            StringBuilder spaces = new StringBuilder();
            spaces.insert(0, String.format("%" + length + "s", ""));
            return (s + spaces);
        }

        private String currentDateandTime() {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            return sdf.format(new Date());
        }

    }

    public void splitString(String msg, int lineSize, ArrayList<String> arrayList) {
        arrayList.clear();
        Pattern p = Pattern.compile("\\b.{0," + (lineSize - 1) + "}\\b\\W?");
        Matcher m = p.matcher(msg);
        while (m.find()) {
            arrayList.add(m.group().trim());
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
