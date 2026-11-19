package com.shardx.hotelManagement.airBnb.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchConversationTest {

    @Test
    void includesTheCityAndClarificationBeforeADatesOnlyReply() {
        String question = "What are your check-in and check-out dates?";
        String dates = "December 12 to December 14";
        var request = new AiHotelSearchRequest(dates, List.of(new AiHotelSearchRequest.Turn("Pune", question)));
        var messages = SpringAiHotelSearchIntentExtractor.conversation(request);

        assertThat(messages).extracting(Message::getMessageType)
                .containsExactly(MessageType.USER, MessageType.ASSISTANT, MessageType.USER);
        assertThat(messages).extracting(Message::getText).containsExactly("Pune", question, dates);
    }
}
