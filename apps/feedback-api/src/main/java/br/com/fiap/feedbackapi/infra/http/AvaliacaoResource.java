package br.com.fiap.feedbackapi.infra.http;

import br.com.fiap.feedbackapi.core.dto.CriarAvaliacaoCommand;
import br.com.fiap.feedbackapi.core.usecase.CriarAvaliacaoUseCase;
import br.com.fiap.feedbackplatform.shared.domain.Feedback;
import br.com.fiap.feedbackplatform.shared.domain.Urgencia;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.UUID;

@Path("/avaliacao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AvaliacaoResource {
    @Context
    ContainerRequestContext requestContext;

    private final CriarAvaliacaoUseCase criarAvaliacaoUseCase;

    public AvaliacaoResource(CriarAvaliacaoUseCase criarAvaliacaoUseCase) {
        this.criarAvaliacaoUseCase = criarAvaliacaoUseCase;
    }

    @POST
    public Response criar(@Valid @NotNull CriarAvaliacaoRequest request) {

        var correlationId = CorrelationIdProvider.get(requestContext);
        Feedback feedback = criarAvaliacaoUseCase.execute(
                new CriarAvaliacaoCommand(request.descricao(), request.nota(), correlationId));
        CriarAvaliacaoResponse response = new CriarAvaliacaoResponse(
                feedback.id(),
                "CREATED",
                feedback.urgencia(),
                feedback.dataEnvio());

        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    public record CriarAvaliacaoRequest(
            @NotBlank @Size(min = 10, max = 1000, message = "Descrição deve ter entre 10 e 1000 caracteres") String descricao,
            @NotNull @Min(value = 0, message = "nota não pode ser menor que 0") @Max(value = 10, message = "nota não pode ser maior que 10") Integer nota) {
    }

    public record CriarAvaliacaoResponse(UUID id, String status, Urgencia urgencia, Instant dataEnvio) {
    }
}
