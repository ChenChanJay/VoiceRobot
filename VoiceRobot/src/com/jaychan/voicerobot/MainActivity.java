package com.jaychan.voicerobot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.jaychan.voicerobot.VoiceBean.WSBean;
import com.jaychan.voicerobot.utils.ArrayListUtils;
import com.jaychan.voicerobot.utils.PrefUtils;
import com.lidroid.xutils.HttpUtils;
import com.lidroid.xutils.ViewUtils;
import com.lidroid.xutils.exception.HttpException;
import com.lidroid.xutils.http.RequestParams;
import com.lidroid.xutils.http.ResponseInfo;
import com.lidroid.xutils.http.callback.RequestCallBack;
import com.lidroid.xutils.http.client.HttpRequest.HttpMethod;
import com.lidroid.xutils.view.annotation.ViewInject;

public class MainActivity extends ActionBarActivity {

	@ViewInject(R.id.lv_list)
	private ListView lvList;

	@ViewInject(R.id.et_content)
	private EditText etContent;

	@ViewInject(R.id.btn_sendText)
	private Button btnSendText;

	@ViewInject(R.id.btn_sendVoice)
	private Button btnSendVoice;

	@ViewInject(R.id.iv_voice)
	private ImageView ivVoice;

	@ViewInject(R.id.iv_text)
	private ImageView ivText;

	private boolean read;

	private ArrayList<ChatBean> mChatList;

	private String[] mMMAnswers = new String[] { "约吗?", "讨厌!", "不要再要了!",
			"这是最后一张了!", "漂亮吧?" };

	private int[] mMMImageIDs = new int[] { R.drawable.p1, R.drawable.p2,
			R.drawable.p3, R.drawable.p4 };

	public static final String APIKEY = "d289192d2f76e474ae9f38bce86e57e6";
	// 图灵参数请求前缀
	public static final String URL = "http://www.tuling123.com/openapi/api";
	// 由开发者自己分配的用户id,用于上下文信息
	public static final int userid = 12345678;
	// 图灵参数请求后缀
	public static final String SUFFIX = "&userid=" + userid;

	private String speaker;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		options.add("清除聊天记录");
		options.add("发音语言");
		options.add("退出");

		speaker = PrefUtils.getString(this, "speaker", "xiaoyu");
		mCurrentChooseItem = PrefUtils.getInt(this, "mCurrentChooseItem", 0);
		mCurrentItem = PrefUtils.getInt(this, "mCurrentItem", 0);
		read = PrefUtils.getBoolean(this, "read", true);

		String record = PrefUtils.getString(this, "record", "");
		System.out.println("read record:" + record);
		try {
			mChatList = (ArrayList<ChatBean>) ArrayListUtils
					.String2SceneList(record);
			System.out.println(1);
			System.out.println("size " + mChatList.size());
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (mChatList == null) {
			System.out.println(2);
			mChatList = new ArrayList<ChatBean>();
		}

		ViewUtils.inject(this, this);

		mAdapter = new chatAdapter();
		lvList.setAdapter(mAdapter);
		lvList.setSelection(mChatList.size() - 1);
		// 初始化语音引擎
		SpeechUtility.createUtility(this, SpeechConstant.APPID + "=54b8bca3");

		// 给EditText设置监听
		etContent.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				if (!TextUtils.isEmpty(s)) {
					btnSendText.setEnabled(true);
				} else {
					btnSendText.setEnabled(false);
				}
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {

			}

			@Override
			public void afterTextChanged(Editable s) {

			}
		});

	}

	public boolean onCreateOptionsMenu(android.view.Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return super.onCreateOptionsMenu(menu);
	};

	public boolean onOptionsItemSelected(android.view.MenuItem item) {

		switch (item.getOrder()) {

		case 101:
			cleanRecord();
			break;

		case 102:
			showChooseDialog();
			break;

		case 103:
			finish();
			break;

		default:
			break;
		}

		return super.onOptionsItemSelected(item);
	};

	StringBuffer mTextBuffer = new StringBuffer();

	// 语音输入
	private RecognizerDialogListener recognizerDialogListener = new RecognizerDialogListener() {

		@Override
		public void onResult(RecognizerResult result, boolean isLast) {
			System.out.println(result.getResultString());
			System.out.println("isLast:" + isLast);

			String text = parseData(result.getResultString());
			mTextBuffer.append(text);

			if (isLast) { // 会话结束
				String finalText = mTextBuffer.toString();
				mTextBuffer = new StringBuffer(); // 清理buffer
				System.out.println("最终结果:" + finalText);
				mChatList.add(new ChatBean(finalText, true, -1));
				mAdapter.notifyDataSetChanged();

				if (mTts != null && mTts.isSpeaking()) {
					mTts.stopSpeaking();
				}
				getAnswer(finalText);
			}

		}

		@Override
		public void onError(SpeechError arg0) {

		}
	};

	/**
	 * 语音朗诵
	 */
	public void read(String text, boolean read) {

		if (read) {
			mTts = SpeechSynthesizer.createSynthesizer(this, null);
			mTts.setParameter(SpeechConstant.VOICE_NAME, speaker);
			mTts.setParameter(SpeechConstant.SPEED, "50");
			mTts.setParameter(SpeechConstant.VOLUME, "80");
			mTts.setParameter(SpeechConstant.ENGINE_TYPE,
					SpeechConstant.TYPE_CLOUD);

			mTts.startSpeaking(text, null);
		}
	}

	private chatAdapter mAdapter;

	private SpeechSynthesizer mTts;

	/**
	 * 发送语音
	 */
	public void sendVoice(View view) {

		RecognizerDialog iatDialog = new RecognizerDialog(this, null);

		// 设置听写参数，详见《科大讯飞MSC API手册(Android)》SpeechConstant类
		iatDialog.setParameter(SpeechConstant.DOMAIN, "iat");
		iatDialog.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
		iatDialog.setParameter(SpeechConstant.ACCENT, "mandarin");

		iatDialog.setListener(recognizerDialogListener);

		iatDialog.show();
	}

	/**
	 * 发送文本
	 */
	public void sendText(View view) {
		String text = etContent.getText().toString().trim();
		mChatList.add(new ChatBean(text, true, -1));
		getAnswer(text);
		etContent.setText("");
	}

	/**
	 * 切换成文本输入类型
	 */
	public void changeTypeToText(View view) {
		// 图标设置成语音
		ivVoice.setVisibility(View.VISIBLE);
		// 隐藏键盘小图标
		ivText.setVisibility(View.INVISIBLE);
		// 显示输入框
		etContent.setVisibility(View.VISIBLE);
		// 隐藏发送语音按钮
		btnSendVoice.setVisibility(View.INVISIBLE);
		// 显示发送按钮
		btnSendText.setVisibility(View.VISIBLE);
	}

	/**
	 * 切换成语音输入类型
	 */
	public void changeTypeToVoice(View view) {
		// 图标设置成键盘
		ivText.setVisibility(View.VISIBLE);
		// 隐藏语音小图标
		ivVoice.setVisibility(View.INVISIBLE);
		// 隐藏输入框
		etContent.setVisibility(View.INVISIBLE);
		// 显示发送语音按钮
		btnSendVoice.setVisibility(View.VISIBLE);
		// 隐藏发送按钮
		btnSendText.setVisibility(View.GONE);
	}

	/**
	 * 解析语音数据
	 * 
	 * @param resultString
	 */
	protected String parseData(String resultString) {
		Gson gson = new Gson();
		VoiceBean bean = gson.fromJson(resultString, VoiceBean.class);
		ArrayList<WSBean> ws = bean.ws;

		StringBuffer sb = new StringBuffer();
		for (WSBean wsBean : ws) {
			String text = wsBean.cw.get(0).w;
			sb.append(text);
		}

		return sb.toString();
	}

	String answer = "没听清";

	// 获取回答
	private void getAnswer(String finalText) {
		int imageId = -1;
		if (finalText.contains("你好")) {
			answer = "大家好,才是真的好!";
			refreshAnswer(imageId);
		} else if (finalText.contains("你是谁")) {
			answer = "我是你的小助手";
			refreshAnswer(imageId);
		} else if (finalText.contains("天王盖地虎")) {
			answer = "小鸡炖蘑菇";
			imageId = R.drawable.m;
			refreshAnswer(imageId);
		} else if (finalText.contains("美女")) {
			Random random = new Random();
			int i = random.nextInt(mMMAnswers.length);
			int j = random.nextInt(mMMImageIDs.length);
			answer = mMMAnswers[i];
			imageId = mMMImageIDs[j];
			refreshAnswer(imageId);
		} else {
			getAnswerFromInternet(finalText);
		}
	}

	// 刷新回答
	public void refreshAnswer(int imageId) {
		mChatList.add(new ChatBean(answer, false, imageId)); // 添加回答数据
		read(answer, read); // 朗诵回复的内容
		mAdapter.notifyDataSetChanged(); // 刷新listview
		lvList.setSelection(mChatList.size() - 1); // 将ListView定位到最后
	}

	// 从网络获取回答
	public void getAnswerFromInternet(String finalText) {
		HttpUtils utils = new HttpUtils();
		// 请求的字符串
		RequestParams params = new RequestParams();
		params.addBodyParameter("key", APIKEY);
		params.addBodyParameter("info", finalText);
		params.addBodyParameter("userid", "123456");

		System.out.println(URL);

		utils.send(HttpMethod.POST, URL, params, new RequestCallBack<String>() {
			@Override
			public void onSuccess(ResponseInfo<String> responseInfo) {
				try {
					// 返回的是json的字符串
					String result = responseInfo.result;

					if (!TextUtils.isEmpty(result)) {
						JSONObject jo = new JSONObject(result);
						answer = jo.getString("text");
						System.out.println("返回结果:" + answer);
						refreshAnswer(-1);
					}

				} catch (JSONException e) {
					e.printStackTrace();
				}

			}

			@Override
			public void onFailure(HttpException error, String msg) {
				Toast.makeText(MainActivity.this, "请检查你的网络连接",
						Toast.LENGTH_SHORT).show();

			}
		});
	}

	class chatAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			return mChatList.size();
		}

		@Override
		public ChatBean getItem(int position) {
			return mChatList.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			ViewHolder holder;

			if (convertView == null) {
				holder = new ViewHolder();
				convertView = View.inflate(MainActivity.this,
						R.layout.list_item, null);

				holder.tvAsk = (TextView) convertView.findViewById(R.id.tv_ask);
				holder.tvAnswer = (TextView) convertView
						.findViewById(R.id.tv_answer);
				holder.llAnswer = (LinearLayout) convertView
						.findViewById(R.id.ll_answer);
				holder.ivPic = (ImageView) convertView
						.findViewById(R.id.iv_pic);
				holder.iconAnswer = (ImageView) convertView
						.findViewById(R.id.icon_answer);
				holder.rlAsker = (RelativeLayout) convertView
						.findViewById(R.id.rl_ask);

				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}

			ChatBean item = getItem(position);

			if (item.isAsker) { // 是提问者
				holder.llAnswer.setVisibility(View.GONE);
				holder.tvAsk.setVisibility(View.VISIBLE);
				holder.rlAsker.setVisibility(View.VISIBLE);
				holder.iconAnswer.setVisibility(View.GONE);
				holder.tvAsk.setText(item.text);
			} else {
				holder.tvAsk.setVisibility(View.GONE);
				holder.llAnswer.setVisibility(View.VISIBLE);
				holder.rlAsker.setVisibility(View.GONE);
				holder.iconAnswer.setVisibility(View.VISIBLE);
				holder.tvAnswer.setText(item.text);

				if (item.imageId != -1) { // 有图片
					holder.ivPic.setVisibility(View.VISIBLE);
					holder.ivPic.setImageResource(item.imageId);
				} else {
					holder.ivPic.setVisibility(View.GONE);
				}

			}

			return convertView;
		}

	}

	static class ViewHolder {
		public TextView tvAsk;
		public TextView tvAnswer;
		public LinearLayout llAnswer;
		public ImageView ivPic;
		public ImageView iconAnswer;
		public RelativeLayout rlAsker;
	}

	private int mCurrentChooseItem; // 记录当前选中的item,点击确定前
	private int mCurrentItem; // 记录当前选中的item,点击确定后

	private Dialog dialog;

	/**
	 * 显示语言选择对话框
	 */
	private void showChooseDialog() {

		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		String[] items = new String[] { "普通话(男)", "普通话(女)", "普通话(台湾)", "粤语",
				"湖南话", "四川话",  "河南话", "陕西话", "不发音" };

		builder.setTitle("机器人发音语言");
		builder.setSingleChoiceItems(items, mCurrentItem,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						System.out.println("选中:" + which);
						mCurrentChooseItem = which;
					}
				});

		builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {

				switch (mCurrentChooseItem) {

				case 0:
					speaker = "xiaoyu";
					read = true;
					break;

				case 1:
					speaker = "xiaoyan";
					read = true;
					break;

				case 2:
					speaker = "vixl";
					read = true;
					break;

				case 3:
					speaker = "vixm";
					read = true;
					break;

				case 4:
					speaker = "vixqa";
					read = true;
					break;

				case 5:
					speaker = "vixr";
					read = true;
					break;


				case 6:
					speaker = "vixk";
					read = true;
					break;

				case 7:
					speaker = "vixying";
					read = true;
					break;

				case 8:
					read = false;
					break;

				default:
					break;
				}
				mCurrentItem = mCurrentChooseItem;
				PrefUtils.setInt(MainActivity.this, "mCurrentItem",
						mCurrentItem);
				PrefUtils.setInt(MainActivity.this, "mCurrentChooseItem",
						mCurrentChooseItem);
				PrefUtils.setString(MainActivity.this, "speaker", speaker);
			}
		});
		builder.setNegativeButton("取消", null);
		builder.show();
	}

	private ArrayList<String> options = new ArrayList<String>();

	/**
	 * 清除聊天记录
	 */
	public void cleanRecord() {
		AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
		builder.setTitle("提示").setMessage("是否清楚聊天记录")
				.setNegativeButton("取消", null)
				.setPositiveButton("确定", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						mChatList.clear();
						mAdapter.notifyDataSetChanged();
					}
				});

		builder.show();
	}

	@Override
	protected void onPause() {
		saveRecord();
		super.onPause();
	}

	@Override
	protected void onStop() {
		saveRecord();
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		saveRecord();
		super.onDestroy();
	}

	private void saveRecord() {
		String record;
		try {
			record = ArrayListUtils.SceneList2String(mChatList);
			System.out.println("record" + record);
			PrefUtils.setString(this, "record", record);

			if (mTts != null && mTts.isSpeaking()) {
				mTts.stopSpeaking();
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}
