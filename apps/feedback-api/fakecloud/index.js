const crypto = require("crypto");

exports.handler = async (event) => {
  const method = event.httpMethod || "";
  const path = routePath(event);

  if (method === "GET" && path === "/health") {
    return json(200, { status: "UP" });
  }

  if (method === "POST" && path === "/avaliacao") {
    let request;
    try {
      request = JSON.parse(event.body || "{}");
    } catch (_error) {
      return json(400, { code: "MALFORMED_JSON", message: "Corpo da requisicao invalido." });
    }

    const validationError = validate(request);
    if (validationError) {
      return json(400, { code: "VALIDATION_ERROR", message: validationError });
    }

    const response = {
      id: crypto.randomUUID(),
      status: "CREATED",
      urgencia: classifyUrgencia(request.nota),
      dataEnvio: new Date().toISOString(),
    };

    if (response.urgencia === "CRITICA") {
      const correlationId = header(event.headers, "x-correlation-id");
      console.log(`Critical feedback publishing is not implemented yet. feedbackId=${response.id} correlationId=${correlationId || ""}`);
    }

    return json(201, response);
  }

  return json(404, { code: "NOT_FOUND", message: "Rota nao encontrada." });
};

function validate(request) {
  if (!request || typeof request !== "object") {
    return "Corpo da requisicao invalido.";
  }
  if (typeof request.descricao !== "string" || request.descricao.trim() === "") {
    return "Campo descricao e obrigatorio.";
  }
  if (request.descricao.length < 10 || request.descricao.length > 1000) {
    return "Descricao deve ter entre 10 e 1000 caracteres.";
  }
  if (!Number.isInteger(request.nota)) {
    return "Campo nota e obrigatorio.";
  }
  if (request.nota < 0 || request.nota > 10) {
    return "Nota deve estar entre 0 e 10.";
  }
  return null;
}

function classifyUrgencia(nota) {
  if (nota <= 3) {
    return "CRITICA";
  }
  if (nota <= 6) {
    return "MEDIA";
  }
  return "BAIXA";
}

function header(headers, name) {
  if (!headers) {
    return null;
  }
  const wanted = name.toLowerCase();
  for (const [key, value] of Object.entries(headers)) {
    if (key.toLowerCase() === wanted) {
      return value;
    }
  }
  return null;
}

function routePath(event) {
  const path = event.path || event.rawPath || "";
  const stage = event.requestContext && event.requestContext.stage;

  if (stage && path.startsWith(`/${stage}/`)) {
    return path.substring(stage.length + 1);
  }

  return path;
}

function json(statusCode, body) {
  return {
    statusCode,
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  };
}
