package com.innsys;

import java.util.ArrayList;
import java.util.List;
import javax.jws.WebService;
import javax.jws.WebMethod;
import javax.jws.WebParam;

@WebService(
    targetNamespace = "http://innsys.com/",
    serviceName = "TamplateServiceWSService",
    portName = "TamplateServiceWSPort"
)
public class TamplateServiceWSDelegate {

    private TamplateServiceWS service = new TamplateServiceWS();

    @WebMethod(operationName = "fillPdfAndStore")
    public String fillPdfAndStore(
        @WebParam(name = "documentId") String documentId,
        @WebParam(name = "inputValues") KeyValuePair[] inputValues
    ) throws Exception {

        List<KeyValuePair> fieldValues = new ArrayList<KeyValuePair>();

        if (inputValues != null) {
            for (KeyValuePair kvp : inputValues) {
                if (kvp != null && kvp.getKey() != null) {
                    fieldValues.add(kvp);
                }
            }
        }

        return service.fillPdfAndStore(documentId, fieldValues);
    }
}
