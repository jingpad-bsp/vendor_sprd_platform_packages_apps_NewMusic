package com.sprd.music.data;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.sprd.music.fragment.ViewHolder;

import java.util.ArrayList;

/**
 * Created by jian.xu on 2017/5/10.
 */

public abstract class MusicListAdapter<T> extends BaseAdapter {
    private Context context;
    private int listItemId = 0;

    public MusicListAdapter(Context context, int listItemId) {
        super();
        this.context = context;
        this.listItemId = listItemId;
    }

    public Context getContext() {
        return context;
    }

    private ArrayList<T> mData = new ArrayList<>();

    public void setData(ArrayList<T> data) {
        if (data != null) {
            mData = data;
        } else {
            mData = new ArrayList<>();
        }
        notifyDataSetChanged();
    }

    public ArrayList<T> getData() {
        return mData;
    }

    @Override
    public int getCount() {
        return mData.size();
    }

    @Override
    public T getItem(int position) {
        return mData.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(listItemId, null);
            ViewHolder viewHolder = new ViewHolder();
            convertView.setTag(viewHolder);
            initViewHolder(convertView, viewHolder);
        }
        initListItem(position, convertView, parent);
        return convertView;
    }

    protected abstract void initViewHolder(View listItem, ViewHolder viewHolder);

    protected abstract void initListItem(int position, View listItem, ViewGroup parent);

}
