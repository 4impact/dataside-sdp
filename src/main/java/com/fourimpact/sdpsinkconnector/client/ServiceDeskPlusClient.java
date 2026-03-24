package com.fourimpact.sdpsinkconnector.client;

import com.fourimpact.sdpsinkconnector.model.sdp.SdpAddNotePayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpCloseRequestPayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpCreateRequestPayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpRequestTemplate;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpUpdateRequestPayload;

public interface ServiceDeskPlusClient {

    String createRequest(SdpCreateRequestPayload payload);

    void updateRequest(String requestId, SdpUpdateRequestPayload payload);

    void addNote(String requestId, SdpAddNotePayload payload);

    void closeRequest(String requestId, SdpCloseRequestPayload payload);

    /**
     * Fetches a single request template by its SDP ID.
     *
     * <p><b>OAuth scope prerequisite:</b> the token must include
     * {@code SDPOnDemand.setup.READ} (or {@code SDPOnDemand.setup.ALL}).
     * The {@code SDPOnDemand.requests.ALL} scope alone is insufficient and
     * will result in HTTP 401.</p>
     *
     * @param templateId the SDP internal template ID (e.g. {@code "7564000000278687"})
     * @return the parsed template, or {@code null} if the response body cannot be parsed
     * @throws com.fourimpact.sdpsinkconnector.exception.PermanentSdpException on 4xx errors
     * @throws com.fourimpact.sdpsinkconnector.exception.TransientSdpException on 5xx / connection errors
     */
    SdpRequestTemplate getRequestTemplate(String templateId);
}
