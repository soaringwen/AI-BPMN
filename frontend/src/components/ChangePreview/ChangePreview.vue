<script setup lang="ts">
import { computed } from 'vue';
import type { ChangePreview } from '@/types/workspace';

const props = defineProps<{
  preview: ChangePreview;
}>();

const emit = defineEmits<{
  apply: [];
  discard: [];
}>();

const hasChanges = computed(
  () =>
    props.preview.changes.added.length > 0 ||
    props.preview.changes.updated.length > 0 ||
    props.preview.changes.deleted.length > 0 ||
    props.preview.changes.flowsAdded.length > 0 ||
    props.preview.changes.flowsUpdated.length > 0 ||
    props.preview.changes.flowsDeleted.length > 0
);
</script>

<template>
  <div class="change-preview">
    <a-alert type="warning" show-icon :message="`AI修改预览：${preview.summary}`" />

    <div class="change-preview__body">
      <div v-if="preview.changes.added.length" class="change-group">
        <div class="change-group__title change-group__title--added">新增元素</div>
        <div v-for="item in preview.changes.added" :key="item.elementId" class="change-item">
          [{{ item.elementType }}] {{ item.name ?? item.elementId }}
        </div>
      </div>

      <div v-if="preview.changes.updated.length" class="change-group">
        <div class="change-group__title change-group__title--updated">修改元素</div>
        <div v-for="item in preview.changes.updated" :key="item.elementId" class="change-item">
          [{{ item.elementType }}] {{ item.name ?? item.elementId }}
        </div>
      </div>

      <div v-if="preview.changes.deleted.length" class="change-group">
        <div class="change-group__title change-group__title--deleted">删除元素</div>
        <div v-for="item in preview.changes.deleted" :key="item.elementId" class="change-item">
          [{{ item.elementType }}] {{ item.name ?? item.elementId }}
        </div>
      </div>

      <div v-if="preview.changes.flowsAdded.length" class="change-group">
        <div class="change-group__title change-group__title--added">新增连线</div>
        <div v-for="item in preview.changes.flowsAdded" :key="item.elementId" class="change-item">
          {{ item.name ?? item.elementId }}
        </div>
      </div>

      <div v-if="preview.changes.flowsDeleted.length" class="change-group">
        <div class="change-group__title change-group__title--deleted">删除连线</div>
        <div v-for="item in preview.changes.flowsDeleted" :key="item.elementId" class="change-item">
          {{ item.name ?? item.elementId }}
        </div>
      </div>

      <a-alert
        v-if="!preview.validation.valid"
        type="error"
        show-icon
        :message="`修改后模型存在校验错误：${preview.validation.errors.map(e => e.message).join('；')}`"
        style="margin-top: 8px"
      />

      <a-empty v-if="!hasChanges" description="无结构变化" />
    </div>

    <div class="change-preview__actions">
      <a-button type="primary" size="small" @click="emit('apply')">应用修改</a-button>
      <a-button size="small" danger @click="emit('discard')">放弃修改</a-button>
    </div>
  </div>
</template>

<style scoped>
.change-preview {
  padding: 10px 12px;
  border-top: 1px solid #f0f0f0;
  background: #fffbe6;
}

.change-preview__body {
  margin-top: 8px;
  max-height: 220px;
  overflow-y: auto;
}

.change-group {
  margin-bottom: 6px;
}

.change-group__title {
  font-weight: 600;
  font-size: 12px;
}

.change-group__title--added {
  color: #52c41a;
}

.change-group__title--updated {
  color: #fa8c16;
}

.change-group__title--deleted {
  color: #ff4d4f;
}

.change-item {
  font-size: 12px;
  padding-left: 12px;
}

.change-preview__actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
</style>
