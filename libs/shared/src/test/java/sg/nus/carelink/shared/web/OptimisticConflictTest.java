package sg.nus.carelink.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class OptimisticConflictTest {
    @RestController static class Probe {
        @GetMapping("/conflict") String conflict() { throw new ObjectOptimisticLockingFailureException("Visit", 1L); }
    }
    @Test void staleManagerWriteIsRetryable409NotServerFailure() throws Exception {
        MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(get("/conflict")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
    }
}
