import { http } from './client';
import type { ProcessModel, ValidationResult } from '@/types/processModel';

export interface ParseXmlResponse {
  processModel: ProcessModel;
  validation: ValidationResult;
}

export async function parseXml(xml: string): Promise<ParseXmlResponse> {
  const { data } = await http.post<ParseXmlResponse>('/api/v1/bpmn/xml/parse', { xml });
  return data;
}

export async function generateXml(processModel: ProcessModel): Promise<string> {
  const { data } = await http.post<{ bpmnXml: string }>('/api/v1/bpmn/xml/generate', { processModel });
  return data.bpmnXml;
}

export async function validateModel(processModel: ProcessModel): Promise<ValidationResult> {
  const { data } = await http.post<{ validation: ValidationResult }>('/api/v1/bpmn/validate/model', {
    processModel
  });
  return data.validation;
}

export async function fullLayout(processModel: ProcessModel): Promise<string> {
  const { data } = await http.post<{ bpmnXml: string }>('/api/v1/bpmn/layout', {
    processModel,
    mode: 'FULL',
    nodeIds: []
  });
  return data.bpmnXml;
}
