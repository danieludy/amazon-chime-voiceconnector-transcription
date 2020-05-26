package com.amazonaws.streamingeventmodel;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
@JsonPropertyOrder(alphabetic=true)
public class Sdp {
    private int mediaIndex;
    private String mediaLabel;
}