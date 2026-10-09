package sg.nus.carelink.profile.domain.model;

import java.util.List;

public record ServiceApplicationPage(List<ServiceApplication> items, int page, int size, long totalElements) {
    public ServiceApplicationPage { items = List.copyOf(items); }
}
