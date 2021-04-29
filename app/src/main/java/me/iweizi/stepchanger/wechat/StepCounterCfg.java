package me.iweizi.stepchanger.wechat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.HashMap;

import me.iweizi.stepchanger.MyApplication;
import me.iweizi.stepchanger.R;
import me.iweizi.stepchanger.StepData;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuFileInputStream;

/**
 * Created by iweiz on 2017/9/4.
 * 微信的步数数据类
 */

class StepCounterCfg extends StepData {
    private static final int CURRENT_TODAY_STEP = 201;
    private static final int PRE_SENSOR_STEP = 203;
    private static final int LAST_SAVE_STEP_TIME = 204;
    /*
        static final int BEGIN_OF_TODAY = 202;
        static final int REBOOT_TIME = 205;
        static final int SENSOR_TIME_STAMP = 209;
    */
    private static final int LAST_UPLOAD_TIME = 3;
    private static final int LAST_UPLOAD_STEP = 4;

    private static final File WX_DATA_DIR = new File("/data/data/com.tencent.mm/MicroMsg/");

    @SuppressLint("SdCardPath")
    private static final String UPLOAD_STATUS_CFG = "MM_stepcounter.cfg";
    @SuppressLint("SdCardPath")
    private static final String V7_LOCAL_STEP_CFG = "PUSH_stepcounter.cfg";
    @SuppressLint("SdCardPath")
    private static final String LEGACY_LOCAL_STEP_CFG = "stepcounter.cfg";

    private static final String WECHAT_PKG = "com.tencent.mm";
    private static StepCounterCfg sStepCounterCfg = null;
    private final String TAG = "WECHAT";
    private HashMap<Integer, ?> mMMStepCounterMap = null;
    private StepBean mStepBean;  // must sync with mod_step_cfg

    private File wx_local_step_cfg;

    private File wx_upload_status_cfg;


    private StepCounterCfg() {
        super();
        mLoadButtonId = R.id.wechat_load_button;
        mStoreButtonId = R.id.wechat_store_button;

        Context ctx = MyApplication.getContext();
        int versionCode = getWeixinVersionCode();
        wx_upload_status_cfg = new SuFile(WX_DATA_DIR, UPLOAD_STATUS_CFG);
        if (versionCode == -1) {
            ROOT_CMD = new String[]{};
            Toast.makeText(ctx, "Weixin is not installed.", Toast.LENGTH_SHORT).show();
            wx_local_step_cfg = null;
            mStepBean = null;
        } else if (versionCode >= 1363) {
            wx_local_step_cfg = new SuFile(WX_DATA_DIR, V7_LOCAL_STEP_CFG);
            mStepBean = new StepBeanV7(wx_local_step_cfg);
        } else {
            wx_local_step_cfg = new SuFile(WX_DATA_DIR, LEGACY_LOCAL_STEP_CFG);
            mStepBean = new StepBeanLegacy(wx_local_step_cfg);
        }

    }

    private int getWeixinVersionCode() {
        Context ctx = MyApplication.getContext();

        PackageManager pm = ctx.getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo("com.tencent.mm", 0);
            return pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    static StepCounterCfg get() {
        if (sStepCounterCfg == null) {
            sStepCounterCfg = new StepCounterCfg();
        }
        return sStepCounterCfg;
    }

    @Override
    protected int read(Context context) {
        if (mStepBean == null) {
            return FAIL;
        }
        InputStream fis;
        ObjectInputStream ois;

        killWechatProcess();
        try {
            mStepBean.read();

            fis = SuFileInputStream.open(wx_upload_status_cfg);
            ois = new ObjectInputStream(fis);
            //noinspection unchecked
            mMMStepCounterMap = (HashMap<Integer, ?>) ois.readObject();
            return SUCCESS;
        } catch (Exception e) {
            Log.e(TAG, "read error", e);
            return FAIL;
        }
    }


    @Override
    protected int write(Context context) {
        if (mStepBean == null) {
            return FAIL;
        }
        try {
            killWechatProcess();
            mStepBean.write(getStep());
            return SUCCESS;
        } catch (Exception e) {
            Log.e(TAG, "write error", e);
            return FAIL;
        }
    }

    @Override
    protected boolean canRead() {
        boolean result = wx_upload_status_cfg.canRead() && (wx_local_step_cfg != null && wx_local_step_cfg.canRead());
        Log.v(TAG, String.format("canRead %s", result));
        return result;
    }

    @Override
    protected boolean canWrite() {
        boolean result =  wx_upload_status_cfg.canWrite() && (wx_local_step_cfg != null && wx_local_step_cfg.canWrite());
        Log.v(TAG, String.format("canWrite %s", result));
        return result;
    }

    @Override
    public long getLastUploadStep() {
        if (mMMStepCounterMap != null) {
            //noinspection unchecked
            return ((HashMap<Integer, Long>) mMMStepCounterMap).get(LAST_UPLOAD_STEP);
        } else {
            return -1;
        }
    }

    @Override
    public long getLastUploadTime() {
        if (mMMStepCounterMap != null) {
            //noinspection unchecked
            return ((HashMap<Integer, Long>) mMMStepCounterMap).get(LAST_UPLOAD_TIME);
        } else {
            return -1;
        }
    }

    @Override
    public long getLastSaveTime() {
        if (mStepBean != null) {
            return mStepBean.lastSaveStepTime;
        } else {
            return -1;
        }
    }

    @Override
    public long getLastSaveSensorStep() {

        if (mStepBean != null) {
            return mStepBean.preSensorStep;
        } else {
            return -1;
        }
    }

    @Override
    public boolean isLoaded() {
        return mMMStepCounterMap != null && mStepBean != null;
    }

    private void killWechatProcess() {
        Shell.su(String.format("am force-stop %s", WECHAT_PKG)).exec();
    }


}
