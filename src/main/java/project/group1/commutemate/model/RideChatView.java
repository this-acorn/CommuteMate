package project.group1.commutemate.model;

import java.util.List;

/** Ride conversation data for an authorized participant. */
public record RideChatView(Ride ride, List<RideMessageView> messages, boolean owner) {

    public RideChatView {
        messages = List.copyOf(messages);
    }
}
