package com.jaychan.voicerobot;


/**
 * 聊天信息的对象
 * @author Koma
 *
 */
public class ChatBean {

	public String text ;   //内容
	public boolean isAsker ;  //true表示提问者,否则是回答者
	public int imageId = -1 ;   //图片id
	
	public ChatBean(String text, boolean isAsker, int imageId) {
		super();
		this.text = text;
		this.isAsker = isAsker;
		this.imageId = imageId;
	}
	
	
}
