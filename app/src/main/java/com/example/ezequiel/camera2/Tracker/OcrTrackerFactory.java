package com.example.ezequiel.camera2.Tracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.annotation.UiThread;

import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;

import java.util.List;

/**
 * Created by kuohsuan on 2018/5/16.
 */

public class OcrTrackerFactory implements MultiProcessor.Factory<TextBlock>{
    private GraphicOverlay mGraphicOverlay;
    private OcrGraphic.OcrUpdateListener ocrUpdateListener;

    public OcrTrackerFactory(GraphicOverlay graphicOverlay, Context context) {
        mGraphicOverlay = graphicOverlay;
        if (context instanceof OcrGraphic.OcrUpdateListener) {
            this.ocrUpdateListener = (OcrGraphic.OcrUpdateListener) context;
        } else {
            throw new RuntimeException("Hosting activity must implement BarcodeUpdateListener");
        }
    }
    @Override
    public Tracker<TextBlock> create(TextBlock textBlock) {
        OcrGraphic graphic = new OcrGraphic(mGraphicOverlay,ocrUpdateListener);
        return new GraphicTracker<>(mGraphicOverlay,graphic);
    }
}

class OcrGraphic extends TrackedGraphic<TextBlock> {

    private int mId;
    private static final int TEXT_COLOR = Color.WHITE;
    private static Paint sRectPaint;
    private static Paint sTextPaint;
    private volatile TextBlock mText;
    private OcrUpdateListener ocrUpdateListener;

    OcrGraphic(GraphicOverlay overlay, OcrUpdateListener ocrUpdateListener) {
        super(overlay);

        this.ocrUpdateListener = ocrUpdateListener;
        if (sRectPaint == null) {
            sRectPaint = new Paint();
            sRectPaint.setColor(TEXT_COLOR);
            sRectPaint.setStyle(Paint.Style.STROKE);
            sRectPaint.setStrokeWidth(4.0f);
        }

        if (sTextPaint == null) {
            sTextPaint = new Paint();
            sTextPaint.setColor(TEXT_COLOR);
            sTextPaint.setTextSize(54.0f);
        }
        // Redraw the overlay, as this graphic has been added.
        postInvalidate();
    }

    public int getId() {
        return mId;
    }

    @Override
    void updateItem(TextBlock item) {
        mText = item;
        if(ocrUpdateListener!=null){
            ocrUpdateListener.onOcrDetected(item);
        }
        postInvalidate();
    }

    public void setId(int id) {
        this.mId = id;
    }

    public TextBlock getTextBlock() {
        return mText;
    }

    /**
     * Checks whether a point is within the bounding box of this graphic.
     * The provided point should be relative to this graphic's containing overlay.
     * @param x An x parameter in the relative context of the canvas.
     * @param y A y parameter in the relative context of the canvas.
     * @return True if the provided point is contained within this graphic's bounding box.
     */
    public boolean contains(float x, float y) {
        TextBlock text = mText;
        if (text == null) {
            return false;
        }
        RectF rect = new RectF(text.getBoundingBox());
        rect.left = translateX(rect.left);
        rect.top = translateY(rect.top);
        rect.right = translateX(rect.right);
        rect.bottom = translateY(rect.bottom);
        return (rect.left < x && rect.right > x && rect.top < y && rect.bottom > y);
    }

    /**
     * Draws the text block annotations for position, size, and raw value on the supplied canvas.
     */
    @Override
    public void draw(Canvas canvas) {
        TextBlock text = mText;
        if (text == null) {
            return;
        }

        // Draws the bounding box around the TextBlock.
        RectF rect = new RectF(text.getBoundingBox());
        rect.left = translateX(rect.left);
        rect.top = translateY(rect.top);
        rect.right = translateX(rect.right);
        rect.bottom = translateY(rect.bottom);
        canvas.drawRect(rect, sRectPaint);

        // Break the text into multiple lines and draw each one according to its own bounding box.
        List<? extends Text> textComponents = text.getComponents();
        for(Text currentText : textComponents) {
            float left = translateX(currentText.getBoundingBox().left);
            float bottom = translateY(currentText.getBoundingBox().bottom);
            canvas.drawText(currentText.getValue(), left, bottom, sTextPaint);
        }
    }

    /**
     * Consume the item instance detected from an Activity or Fragment level by implementing the
     * BarcodeUpdateListener interface method onBarcodeDetected.
     */
    public interface OcrUpdateListener {
        @UiThread
        void onOcrDetected(TextBlock mText);
    }
}

