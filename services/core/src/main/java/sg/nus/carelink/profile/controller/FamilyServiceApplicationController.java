package sg.nus.carelink.profile.controller;

import java.security.Principal;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sg.nus.carelink.profile.application.FamilyServiceApplicationService;
import sg.nus.carelink.profile.controller.dto.FamilyServiceApplicationResponse;
import sg.nus.carelink.profile.controller.dto.ServiceApplicationCreateRequest;
import sg.nus.carelink.profile.controller.dto.ServiceApplicationListRequest;
import sg.nus.carelink.profile.domain.model.ServiceApplication;

@RestController
@RequestMapping("/api/family/service-applications")
@PreAuthorize("hasRole('FAMILY')")
public class FamilyServiceApplicationController {
    private final FamilyServiceApplicationService applications;

    public FamilyServiceApplicationController(FamilyServiceApplicationService applications) {
        this.applications = applications;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyServiceApplicationResponse submit(@Valid @RequestBody ServiceApplicationCreateRequest request,
            Principal principal) {
        return withProgress(applications.submit(principal.getName(),
                request.elderId(), request.careNeeds(), request.notes()));
    }

    @GetMapping
    public FamilyServiceApplicationResponse.Page list(@Valid @ModelAttribute ServiceApplicationListRequest request,
            Principal principal) {
        var result = applications.list(principal.getName(), request.getPage(), request.getSize());
        var progress = applications.progress(result.items());
        return new FamilyServiceApplicationResponse.Page(result.items().stream()
                .map(item -> FamilyServiceApplicationResponse.from(item, progress.get(item.id()))).toList(),
                result.page(), result.size(), result.totalElements());
    }

    @GetMapping("/{id}")
    public FamilyServiceApplicationResponse get(@PathVariable Long id, Principal principal) {
        return withProgress(applications.get(principal.getName(), id));
    }

    private FamilyServiceApplicationResponse withProgress(ServiceApplication application) {
        return FamilyServiceApplicationResponse.from(application,
                applications.progress(List.of(application)).get(application.id()));
    }
}
