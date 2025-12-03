package com.jelly.cinema.film.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 电影 VO
 *
 * @author Jelly Cinema
 */
@Data
public class FilmVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    private String coverUrl;

    private String videoUrl;

    private String description;

    private Long categoryId;

    private String categoryName;

    private List<String> tags;

    private Double rating;

    private Long playCount;

    private Integer year;

    private String director;

    private String actors;

    private String region;

    private Integer duration;

    private LocalDateTime createTime;
}
