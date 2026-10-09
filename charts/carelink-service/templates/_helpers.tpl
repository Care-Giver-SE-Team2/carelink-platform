{{/* The labels that pick out this service's pods. The release name is the service name. */}}
{{- define "carelink-service.selectorLabels" -}}
app.kubernetes.io/name: {{ .Release.Name }}
{{- end }}

{{/* Labels on everything the chart creates */}}
{{- define "carelink-service.labels" -}}
{{ include "carelink-service.selectorLabels" . }}
app.kubernetes.io/part-of: carelink
app.kubernetes.io/version: {{ .Values.image.tag | trunc 63 | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end }}
