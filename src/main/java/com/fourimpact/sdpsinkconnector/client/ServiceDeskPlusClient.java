package com.fourimpact.sdpsinkconnector.client;

import com.fourimpact.sdpsinkconnector.model.sdp.SdpAddNotePayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpCloseRequestPayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpCreateRequestPayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpUpdateRequestPayload;

public interface ServiceDeskPlusClient {

    String createRequest(SdpCreateRequestPayload payload);

    void updateRequest(String requestId, SdpUpdateRequestPayload payload);

    void addNote(String requestId, SdpAddNotePayload payload);

    void closeRequest(String requestId, SdpCloseRequestPayload payload);
}
