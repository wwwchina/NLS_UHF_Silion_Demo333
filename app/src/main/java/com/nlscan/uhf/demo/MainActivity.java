package com.nlscan.uhf.demo;

import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceActivity;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.nlscan.android.uhf.UHFManager;
import com.nlscan.android.uhf.UHFModuleInfo;
import com.nlscan.android.uhf.UHFReader;
import com.nlscan.uhf.demo.settings.UHFSilionParams;

public class MainActivity extends BasePrefenceActivity implements ISettingChangeListener{

	private UHFManager mUHFMgr = UHFManager.getInstance();
	private HeaderAdapter mAdapter = null;
	private Dialog mRestoreDialog = null;
	private ProgressDialog mRestoreProgDialog = null;
	private Dialog mExitConfirmDialog = null;
	
	private ProgressDialog mCheckingProgDialog = null;
	private Dialog mReLoadDialog = null;
	
	private boolean mModuleAvailable =  false;
	
	private final static int MSG_UHF_POWER_ON = 0x01;
	private final static int MSG_DISMISS_DIALOG = 0x02;
	private final static int MSG_POWER_ON_COMPLETE = 0x03;
	private final static int MSG_UPDATE_VIEW = 0x04;
	private final static int MSG_RESTORE_SUCCESS = 0x05;
	private final static int MSG_RESTORE_FAILED = 0x06;
	private final static int MSG_RELOAD_MODULE_DELAY = 0x07;
	private final static int MSG_LOAD_MODULE_COMPLETED = 0x08;
	
	private Handler mHandler = new Handler(){

		@Override
		public void handleMessage(Message msg) {
			
			switch (msg.what) 
			{
			case MSG_UHF_POWER_ON :
					boolean on = (Boolean) msg.obj;
					if(on)
					{
						Thread t = new Thread(new Runnable() {
							@Override
							public void run() {
								UHFReader.READER_STATE er = mUHFMgr.powerOn();
								Message.obtain(mHandler, MSG_POWER_ON_COMPLETE, er).sendToTarget();
							}
						});
						t.start();
					}else{
						mUHFMgr.powerOff();
						mAdapter.notifyDataSetChanged();
					}
				break;
			case MSG_DISMISS_DIALOG:
				if(mDialog != null)
					mDialog.dismiss();
				break;
			case MSG_POWER_ON_COMPLETE:
				if(mDialog != null)
					mDialog.dismiss();
				mAdapter.notifyDataSetChanged();
				UHFReader.READER_STATE er = (UHFReader.READER_STATE)msg.obj;
				if(er != UHFReader.READER_STATE.OK_ERR)
					Toast.makeText(getApplicationContext(), "failed : "+er.toString(), Toast.LENGTH_SHORT).show();
				break;
			case MSG_UPDATE_VIEW :
				initHeaders(getHeaderList());
				mAdapter.notifyDataSetChanged();
				break;
			case MSG_RESTORE_SUCCESS :
				if(mRestoreProgDialog != null)
					mRestoreProgDialog.dismiss();
				Toast.makeText(getApplicationContext(), R.string.success, Toast.LENGTH_SHORT).show();
				sendEmptyMessage(MSG_UPDATE_VIEW);
				break;
			case MSG_RESTORE_FAILED :
				if(mRestoreProgDialog != null)
					mRestoreProgDialog.dismiss();
				Toast.makeText(getApplicationContext(), R.string.failed, Toast.LENGTH_SHORT).show();
				break;
			case MSG_LOAD_MODULE_COMPLETED:
				if(mCheckingProgDialog != null)
					mCheckingProgDialog.dismiss();
				
				mModuleAvailable = (mUHFMgr.getUHFModuleInfo() == null ? false : true);
				if(mModuleAvailable)
					sendEmptyMessage(MSG_UPDATE_VIEW);
				else{
					showReloadModuleWindow();//显示重新检测的对话框
				}
				break;
			case MSG_RELOAD_MODULE_DELAY:
				reLoadModule();
				break;
			}//end switch
		}
		
	};
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initActionBar();
		//动态权限申请
		PermissionUtils.requestAllRuntimePermission(MainActivity.this);
		
		mModuleAvailable = (mUHFMgr.getUHFModuleInfo() != null);
		if(!mModuleAvailable)
			mHandler.sendEmptyMessageDelayed(MSG_RELOAD_MODULE_DELAY, 50);
	}
	
	
	@Override
	protected void onResume() {
		super.onResume();
		if(mUHFMgr.isPowerOn())
			startQuickMode();
	}


	@Override
	public void onBuildHeaders(List<Header> headers) {
		loadHeadersFromResource(R.xml.uhf_settings_headers, headers);
		filterHeaders(headers);//过滤显示项
		initHeaders(headers);
	}

	@Override
	public void setListAdapter(ListAdapter paramListAdapter) {
		if (mAdapter==null) {
			mAdapter = new HeaderAdapter(getApplicationContext(), 0, getHeaderList());
		}
		super.setListAdapter(mAdapter);
	}
	
	
	@Override
	public void onHeaderClick(Header header, int position) 
	{
		if(!mModuleAvailable)
			return ;
		
		UHFModuleInfo moduleInfo = mUHFMgr.getUHFModuleInfo();
		String modulePackage = moduleInfo == null?null:moduleInfo.packageName;
		
		switch ((int) header.id)
		{
		case R.id.power_enable:
			boolean on = mUHFMgr.isPowerOn()?false:true;
			Message msg = Message.obtain(mHandler, MSG_UHF_POWER_ON, on);
			msg.sendToTarget();
			break;
		case R.id.trigger_mode:
			showTriggerMode();
			break;
		case R.id.inventory_prompt:
			showPromptSetting();
			break;
		case R.id.inventory_demo:
			if( !mUHFMgr.isPowerOn() )
				break;
			try {
				header.intent.addCategory(Intent.CATEGORY_DEFAULT);
				startActivity(header.intent);
			} catch (Exception e) {
				e.printStackTrace();
			}
			break;
		case R.id.read_write_lock:
			if( !mUHFMgr.isPowerOn() )
				break;
			try {
				header.intent.addCategory(Intent.CATEGORY_DEFAULT);
				if(modulePackage != null)
					header.intent.setPackage(modulePackage);
				startActivity(header.intent);
			} catch (Exception e) {
				e.printStackTrace();
			}
			break;
		case R.id.uhf_func_settings:
			if( !mUHFMgr.isPowerOn() )
				break;
			try {
				header.intent.addCategory(Intent.CATEGORY_DEFAULT);
				if(modulePackage != null)
					header.intent.setPackage(modulePackage);
				startActivity(header.intent);
			} catch (Exception e) {
				e.printStackTrace();
			}
			break;
		case R.id.restore_default:
			showRestoreConfirmWindow();
			break;
		default:
			super.onHeaderClick(header, position);
			break;
		}
		
	}
	
	@Override
	protected boolean isValidFragment(String fragmentName) {
		return true;
	}

	
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if(keyCode == KeyEvent.KEYCODE_BACK)
		{
			if(mUHFMgr.isPowerOn())
			{
				showExitPromptWindow();
				return true;
			}
		}
		return super.onKeyUp(keyCode, event);
	}


	@Override
	protected void onDestroy() {
		super.onDestroy();
	}


	private void initActionBar() {
		getActionBar().setDisplayShowHomeEnabled(false);
		getActionBar().setDisplayShowCustomEnabled(true);
		getActionBar().setCustomView(R.layout.action_bar);
		((TextView) findViewById(R.id.tv_title)).setText(getTitle());

		ImageView leftHome = (ImageView) findViewById(R.id.img_home);
		leftHome.setVisibility(View.VISIBLE);
		leftHome.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				if(mUHFMgr.isPowerOn())
					showExitPromptWindow();
				else
					finish();
			}
		});
	}
	
	private List<PreferenceActivity.Header> getHeaderList()
	{
		try {
			java.lang.reflect.Method getHeaders = this.getClass().getMethod("getHeaders");
			return (List<PreferenceActivity.Header>)getHeaders.invoke(MainActivity.this);
		} catch (Exception e) {
			// TODO: handle exception
		}
		return null;
	}
	
	/**
	 * 过滤显示项
	 * @param headers
	 */
	private void filterHeaders(List<PreferenceActivity.Header> headers)
	{
		if (headers != null) {
		}
	}
	
	private void initHeaders(List<PreferenceActivity.Header> headers)
	{
		if (headers != null) 
		{
			for (Header header : headers) 
			{
				switch ((int) header.id) {
				case R.id.uhf_func_settings:
					String model = mUHFMgr.getUHFDeviceModel();
					header.summary = (model == null ?"unknown" : model);
					break;
				case R.id.inventory_prompt:
					boolean soundPrompt = mUHFMgr.isPromptSoundEnable();
					boolean vibratePrompt = mUHFMgr.isPromptVibrateEnable();
					boolean ledPrompt = mUHFMgr.isPromptLEDEnable();
					String prompt_summary = "";
					if(soundPrompt)
						prompt_summary += getString(R.string.scan_prompt_sound);
					if(vibratePrompt)
					{
						if(!TextUtils.isEmpty(prompt_summary))
							prompt_summary += ",";
						prompt_summary +=  getString(R.string.scan_prompt_vibrator);
					}
					if(ledPrompt)
					{
						if(!TextUtils.isEmpty(prompt_summary))
							prompt_summary += ",";
						prompt_summary +=  getString(R.string.scan_prompt_led);
					}
					header.summary = prompt_summary;
					break;
				}
				
			}//end for
		}

	}// end initHeaders
	
	private void showTriggerMode() {
		new TriggerModeSettingFragment().show(getFragmentManager(), "triggermode");
	}
	
	private void showPromptSetting() {
		new PromptSettingFragment().show(getFragmentManager(), "promptsetting");
	}
	
	
	
	@Override
	protected void uhfPowerOning() {
		mHandler.sendEmptyMessage(MSG_UPDATE_VIEW);
		super.uhfPowerOning();
	}


	@Override
	protected void uhfPowerOn() {
		super.uhfPowerOn();
		mHandler.sendEmptyMessage(MSG_UPDATE_VIEW);
		//上电成功,开启快速模式
		startQuickMode();
	}


	@Override
	protected void uhfPowerOff() {
		super.uhfPowerOff();
		mHandler.sendEmptyMessage(MSG_UPDATE_VIEW);
	}


	private synchronized void showRestoreConfirmWindow()
	{
		if(mRestoreDialog != null)
		{
			if(!mRestoreDialog.isShowing())
				mRestoreDialog.show();
			return ;
		}
		
		AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
		builder.setTitle(R.string.restore_default);
		builder.setMessage(getString(R.string.restore_default_promt));
		builder.setPositiveButton(R.string.common_confirm, new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				showRestoringWindow();
				new Thread(new Runnable() {
					
					@Override
					public void run() {
						boolean suc = mUHFMgr.restoreDefaultSettings();
						if(suc)
							mHandler.sendEmptyMessage(MSG_RESTORE_SUCCESS);
						else
							mHandler.sendEmptyMessage(MSG_RESTORE_FAILED);
					}
				}).start();
			}
		});
		
		builder.setNegativeButton(R.string.common_cancel, new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		
		mRestoreDialog = builder.create();
		mRestoreDialog.show();
	}
	
	private  synchronized void showRestoringWindow()
	{
		if(mPaused)
			return ;
		
		mRestoreProgDialog = new ProgressDialog(MainActivity.this);
		mRestoreProgDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);// 设置进度条的形式为圆形转动的进度条
		mRestoreProgDialog.setCancelable(true);// 设置是否可以通过点击Back键取消
		mRestoreProgDialog.setCanceledOnTouchOutside(false);// 设置在点击Dialog外是否取消Dialog进度条
        // 设置提示的title的图标，默认是没有的，如果没有设置title的话只设置Icon是不会显示图标的
		mRestoreProgDialog.setMessage(getString(R.string.common_onning));
		mRestoreProgDialog.show();
	}
	
	/**
	 * 上电状态下退出时,提示是否下电
	 */
	private void showExitPromptWindow()
	{
		synchronized (this) {
			if(mExitConfirmDialog != null)
			{
				if(!mExitConfirmDialog.isShowing())
					mExitConfirmDialog.show();
				return ;
			}
			
			AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
			builder.setTitle(R.string.common_tip);
			builder.setMessage(getString(R.string.power_off_prompt));
			builder.setPositiveButton(R.string.do_power_down, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					mUHFMgr.powerOff();
					finish();
				}
			});
			
			builder.setNegativeButton(R.string.common_no, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					finish();
				}
			});
			
			mExitConfirmDialog = builder.create();
			mExitConfirmDialog.show();
		}
	}
	
	//重新检测模块
	private void reLoadModule()
	{
		synchronized (this) {
			
			if(mPaused)
				return ;
			
			if(mCheckingProgDialog != null)
			{
				if(!mCheckingProgDialog.isShowing())
					mCheckingProgDialog.show();
			}else{
				mCheckingProgDialog = new ProgressDialog(MainActivity.this);
				mCheckingProgDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);// 设置进度条的形式为圆形转动的进度条
				mCheckingProgDialog.setCancelable(true);// 设置是否可以通过点击Back键取消
				mCheckingProgDialog.setCanceledOnTouchOutside(false);// 设置在点击Dialog外是否取消Dialog进度条
		        // 设置提示的title的图标，默认是没有的，如果没有设置title的话只设置Icon是不会显示图标的
				mCheckingProgDialog.setMessage(getString(R.string.loading_uhf_module));
				mCheckingProgDialog.show();
			}
			
			new Thread(new Runnable() {
				
				@Override
				public void run() {
					mUHFMgr.loadUHFModule();
					mHandler.sendEmptyMessage(MSG_LOAD_MODULE_COMPLETED);
				}
			}).start();
		}
	}
	
	//未检测到模块,弹出窗口
	private void showReloadModuleWindow()
	{
		synchronized (this) {
			
			if(mReLoadDialog != null)
			{
				if(!mReLoadDialog.isShowing())
					mReLoadDialog.show();
				return ;
			}
			
			AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
			builder.setTitle(R.string.common_tip);
			builder.setMessage(getString(R.string.uhf_module_unavailable));
			builder.setPositiveButton(R.string.search_again, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					mHandler.sendEmptyMessageDelayed(MSG_RELOAD_MODULE_DELAY, 50);
				}
			});
			
			builder.setNegativeButton(R.string.common_exit, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					finish();
				}
			});
			
			mReLoadDialog = builder.create();
			mReLoadDialog.setCanceledOnTouchOutside(false);
			mReLoadDialog.show();
		}
	}
	
	private void startQuickMode()
	{
		//开启快速模式
		UHFReader.READER_STATE er = mUHFMgr.setParam(UHFSilionParams.INV_QUICK_MODE.KEY, UHFSilionParams.INV_QUICK_MODE.PARAM_INV_QUICK_MODE, "1");
		if (er == UHFReader.READER_STATE.OK_ERR) {
			er = mUHFMgr.setParam(UHFSilionParams.POTL_GEN2_SESSION.KEY, UHFSilionParams.POTL_GEN2_SESSION.PARAM_POTL_GEN2_SESSION, "1");
		}
	}
	
	/*---------------------------------------------------------------------------------------------------------------------------------------------------
	 * Inner Class
	 * ---------------------------------------------------------------------------------------------------------------------------------------------------
	 */
	private class HeaderAdapter extends ArrayAdapter<PreferenceActivity.Header> {
		private Context mContext;
		private List<PreferenceActivity.Header> mHeaderList;
		private LayoutInflater mInflater;

		public HeaderAdapter(Context context, int resource, List<PreferenceActivity.Header> headers) {
			super(context, resource, headers);
			mContext = context;
			mInflater = LayoutInflater.from(mContext);
			mHeaderList = headers;
		}

		public int getCount() {
			if (mHeaderList == null)
				return 0;
			return mHeaderList.size();
		}

		public PreferenceActivity.Header getItem(int paramInt) {
			if (mHeaderList == null)
				return null;
			return (PreferenceActivity.Header) mHeaderList.get(paramInt);
		}

		public long getItemId(int paramInt) {
			PreferenceActivity.Header header = getItem(paramInt);
			if (header == null)
				return 0L;
			return header.id;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			PreferenceActivity.Header localHeader = (PreferenceActivity.Header) mHeaderList.get(position);
			Holder localHolder;
			if (convertView == null) {
				localHolder = new Holder();
				convertView = mInflater.inflate(R.layout.list_item_main_face, null);
				localHolder.icon = ((ImageView) convertView.findViewById(R.id.icon));
				localHolder.tv_title = ((TextView) convertView.findViewById(R.id.tv_title));
				localHolder.tv_summary = ((TextView) convertView.findViewById(R.id.tv_summary));
				localHolder._switch = (CheckBox) convertView.findViewById(R.id.switchWidget);
				convertView.setTag(localHolder);

			} else
				localHolder = (Holder) convertView.getTag();

			localHolder.icon.setImageResource(localHeader.iconRes);
			localHolder.icon.setVisibility(View.GONE);
			localHolder.tv_title.setText(localHeader.titleRes);
			localHolder.tv_summary.setVisibility(View.VISIBLE);
			if (localHeader.summaryRes > 0)
				localHolder.tv_summary.setText(localHeader.summaryRes);
			else if (!TextUtils.isEmpty(localHeader.summary)) {
				localHolder.tv_summary.setText(localHeader.summary);
			} else
				localHolder.tv_summary.setVisibility(View.GONE);

			if (localHeader.id == R.id.power_enable) {
				localHolder._switch.setId(R.id.power_enable);
				localHolder._switch.setVisibility(View.VISIBLE);
				localHolder._switch.setChecked(mModuleAvailable && mUHFMgr.isPowerOn());
				localHolder.tv_title.setTextColor( mModuleAvailable ? Color.BLACK : Color.LTGRAY);
			} else if(localHeader.id == R.id.trigger_mode 
					|| localHeader.id == R.id.inventory_prompt
					|| localHeader.id == R.id.restore_default){
				localHolder._switch.setId(R.id.switchWidget);
				localHolder._switch.setVisibility(View.GONE);
				localHolder.tv_title.setTextColor(mModuleAvailable ? Color.BLACK : Color.LTGRAY);
			}else {
				localHolder._switch.setId(R.id.switchWidget);
				localHolder._switch.setVisibility(View.GONE);
				localHolder.tv_title.setTextColor(mModuleAvailable && mUHFMgr.isPowerOn() ? Color.BLACK : Color.LTGRAY);
			}
			return convertView;
		}

		private class Holder {
			public ImageView icon;
			public TextView tv_summary;
			public TextView tv_title;
			public CheckBox _switch;
		}
	}

	@Override
	public void onSettingsChange() {
		mHandler.sendEmptyMessage(MSG_UPDATE_VIEW);
	}
}
