package com.example.android.tflitecamerademo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;

public class AccelPlot extends View {

    ArrayList<Float> array = new ArrayList<>(10);
    int offset = 0;

    public AccelPlot(Context context) {
        super(context);
    }

    public AccelPlot(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AccelPlot(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AccelPlot(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        //create graph
        Paint p = new Paint();
        p.setColor(Color.BLACK);
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(30);

        // vertical lines
//        for(int i = 0; i < 10; i++) {
//            canvas.drawLine((this.getWidth()-100)/10*i+100, 0, (this.getWidth()-100)/10*i+100, this.getHeight()-100, p);
//        }
//        // horizontal lines
//        for(int i = 0; i < 6; i++) {
//            canvas.drawLine(100, (this.getHeight() - 100) / 5 * i, this.getWidth(), (this.getHeight() - 100) / 5 * i, p);
//        }
        // add y-axis measurements
        for(int i=5; i>=0; i--) {
            int num = 50 - i * 10;
            canvas.drawText(""+num, 50, (this.getHeight()-100)/5*i, p);
        }
        // add x-axis measurements
        for(int i = 0; i < 10; i++) {
            int label = i + offset;
            canvas.drawText(""+label, (this.getWidth()-100)/10*i+100, this.getHeight()-50, p);
        }
        offset++;
        //add label to y-axis
        p.setTextSize(50);
        canvas.drawText("D", 0, 250, p);
        canvas.drawText("A", 0, 350, p);
        canvas.drawText("T", 0, 450, p);
        canvas.drawText("A", 0, 550, p);

        //add label to x-axis
        canvas.drawText("Time (x100 msec)", this.getWidth()/3, this.getHeight(), p);

        ArrayList<Float> x_vals = new ArrayList<>(10);
        ArrayList<Float> y_vals = new ArrayList<>(10);

        Paint p1 = new Paint();
        p1.setColor(Color.BLUE);
        p1.setAlpha(80);
        p1.setStyle(Paint.Style.FILL);

//        Paint p2 = new Paint();
//        p2.setColor(Color.RED);
//        p2.setStyle(Paint.Style.FILL);
//
//        Paint p3 = new Paint();
//        p3.setColor(Color.GREEN);
//        p3.setStyle(Paint.Style.FILL);

        // creates data points
        for (int i = 0; i < array.size(); i++) {
            float x_coor = (float) (this.getWidth()-100) / 10 * i +100;
            x_vals.add(x_coor);
            float y_coor = (float) (this.getHeight()-100) - (((this.getHeight()-100) / 50) * array.get(i));
            y_vals.add(y_coor);
            canvas.drawCircle(x_coor, y_coor, 15, p1);

        }
        // create lines connecting data points
        p1.setStyle(Paint.Style.STROKE);
        p1.setStrokeWidth(7);
//        p2.setStyle(Paint.Style.STROKE);
//        p2.setStrokeWidth(7);
//        p3.setStyle(Paint.Style.STROKE);
//        p3.setStrokeWidth(7);

        if (array.size() >= 2) {
            for (int i = 0; i < array.size(); i++) {
                if(i < array.size()-1) {
                    canvas.drawLine(x_vals.get(i), y_vals.get(i), x_vals.get(i+1), y_vals.get(i+1), p1);
                }
            }
        }


    }


    public void clearList() {
        array.clear();
    }

    public void addPoint(float num) {
        if(array.size() < 10) {
            array.add(num);
        } else if(array.size() == 10) {
            array.remove(0);
            array.add(num);
        }
        Log.v("ACCEL_TAG", "Accel is: " + num);
    }



}
