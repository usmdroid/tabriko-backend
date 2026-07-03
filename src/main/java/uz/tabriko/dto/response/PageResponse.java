package uz.tabriko.dto.response;

import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
public class PageResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static <E, D> PageResponse<D> of(Page<E> page, Function<E, D> mapper) {
        PageResponse<D> r = new PageResponse<>();
        r.content = page.getContent().stream().map(mapper).collect(Collectors.toList());
        r.page = page.getNumber();
        r.size = page.getSize();
        r.totalElements = page.getTotalElements();
        r.totalPages = page.getTotalPages();
        return r;
    }
}
