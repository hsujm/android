package eu.ttbox.geoping.service.encoder;

import android.os.Bundle;

 
public class GeoPingMessage {

    public String phone;
    public SmsMessageActionEnum action;
    public Bundle params;

    public GeoPingMessage() {
        super();
    }

    public GeoPingMessage(String phone, SmsMessageActionEnum action, Bundle params) {
        super();
        this.phone = phone;
        this.action = action;
        this.params = params;
    }

    @Override
    public String toString() {
        return "GeoPingMessage [phone=" + phone + ", action=" + action + ", params=" + params + "]";
    }

}