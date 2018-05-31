package com.techapp.james.bluetooth;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

public class DeviceListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private MainActivity context;
    private ArrayList<BluetoothDevice> data;
    private LayoutInflater layoutInflater;

    DeviceListAdapter(MainActivity context, ArrayList<BluetoothDevice> data) {
        this.context = context;
        this.data = data;
        layoutInflater = LayoutInflater.from(this.context);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = layoutInflater.inflate(R.layout.activity_recycler_item, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
        BluetoothDevice device = data.get(position);
        String name = device.getName();
        String address = device.getAddress();
        itemViewHolder.itemTextView.setText(name);
        itemViewHolder.subTextView.setText(address);
        itemViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((OnItemClick) context).onItemClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    interface OnItemClick {
        void onItemClick(int position);
    }

    class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView itemTextView, subTextView;

        ItemViewHolder(View itemView) {
            super(itemView);
            itemTextView = (TextView) itemView.findViewById(R.id.itemTextView);
            subTextView = (TextView) itemView.findViewById(R.id.subTextView);
        }
    }
}
