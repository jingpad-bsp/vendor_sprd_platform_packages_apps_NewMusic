package com.sprd.music.activity;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import com.android.music.R;

public class ImageAdapter extends BaseAdapter {

    private Context myContext;
    private int[] myImages;
    private int selectId = -1;

    public ImageAdapter(Context c, int[] Images) {
        this.myContext = c;
        this.myImages = Images;
    }

    @Override
    public int getCount() {

        return this.myImages.length;
    }

    @Override
    public Object getItem(int position) {
        return position;
    }


    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder vh;
        if (null == convertView) {
            vh = new ViewHolder();
            convertView = LayoutInflater.from(myContext).inflate(R.layout.gallery_item, null);
            vh.color = (ImageView) convertView.findViewById(R.id.image_color);
            vh.select = (ImageView) convertView.findViewById(R.id.image_select);
            convertView.setTag(vh);
        } else {
            vh = (ViewHolder) convertView.getTag();
        }
        if (selectId == position) {
            vh.select.setVisibility(View.VISIBLE);
        } else {
            vh.select.setVisibility(View.INVISIBLE);
        }
        vh.color.setBackgroundResource(myImages[position]);
        return convertView;
    }

    public void setSelection(int sid) {
        selectId = sid;
    }

    class ViewHolder {
        ImageView color;
        ImageView select;
    }

}
