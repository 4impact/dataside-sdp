package com.fourimpact.sdpsinkconnector.model.sdp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SdpAddNotePayload {

    private String description;
    private Boolean show_to_requester;
    private Boolean mark_first_response;
    private Boolean add_to_linked_requests;
    private Boolean notify_technician;
}
