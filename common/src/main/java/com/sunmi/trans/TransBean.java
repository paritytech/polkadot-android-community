package com.sunmi.trans;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Placeholder Parcelable required by Sunmi's IWoyouService AIDL.
 *
 * The settings printer test does not call commitPrint, but the generated Binder
 * interface still references TransBean.CREATOR when compiling the full app.
 */
public class TransBean implements Parcelable {
    public static final Creator<TransBean> CREATOR = new Creator<TransBean>() {
        @Override
        public TransBean createFromParcel(Parcel in) {
            return new TransBean();
        }

        @Override
        public TransBean[] newArray(int size) {
            return new TransBean[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // This stub only exists for IWoyouService compilation; commitPrint is not used.
    }
}
