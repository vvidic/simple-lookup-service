package net.es.lookup.common;

import java.util.Map;

public abstract class SubRequest extends Message {

    public SubRequest() {

        super();


    }

    public SubRequest(Map<String, Object> map) {

        super(map);

    }

    public void setDefault(){
        this.add(ReservedKeys.RECORD_OPERATOR, ReservedValues.RECORD_OPERATOR_DEFAULT);
    }

}
