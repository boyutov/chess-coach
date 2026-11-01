package com.chesscoach.web.dto;

import com.chesscoach.game.Lesson;

public record LessonResponse(String id, String title, String goal) {

    public static LessonResponse from(Lesson lesson) {
        return new LessonResponse(lesson.name(), lesson.title(), lesson.goal());
    }
}
