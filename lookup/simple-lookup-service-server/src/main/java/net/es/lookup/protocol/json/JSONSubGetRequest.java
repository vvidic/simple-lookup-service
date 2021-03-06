package net.es.lookup.protocol.json;


import net.es.lookup.common.SubGetRequest;
import net.sf.json.JSONObject;
import net.sf.json.util.JSONTokener;

public class JSONSubGetRequest extends SubGetRequest {

    static public final int VALID = 1;
    static public final int INCORRECT_FORMAT = 2;

    public JSONSubGetRequest(String message) {

        this.parseJSON(message);

    }

    private void parseJSON(String message) {

        if (!message.isEmpty()) {

            JSONTokener tokener = new JSONTokener(message);
            Object obj = tokener.nextValue();
            JSONObject jsonObj = (JSONObject) obj;

            for (Object o : ((JSONObject) obj).keySet()) {

                this.add(o.toString(), ((JSONObject) obj).get(o));

            }

        }

        this.status = JSONSubGetRequest.VALID;

    }


}


