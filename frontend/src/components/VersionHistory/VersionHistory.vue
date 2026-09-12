<script setup lang="ts">
import { useWorkspaceStore } from '@/stores/workspaceStore';
import type { WorkspaceSnapshot } from '@/types/workspace';

const store = useWorkspaceStore();

const emit = defineEmits<{
  restore: [snapshot: WorkspaceSnapshot];
}>();

const sourceLabels: Record<WorkspaceSnapshot['source'], string> = {
  AI_CREATE: 'AI创建',
  AI_MODIFY: 'AI修改',
  MANUAL: '手工编辑',
  IMPORT: '导入',
  RESTORE: '版本恢复'
};
</script>

<template>
  <div class="version-history">
    <a-empty v-if="store.history.length === 0" description="暂无版本快照" />
    <a-timeline v-else>
      <a-timeline-item
        v-for="(snapshot, index) in [...store.history].reverse()"
        :key="snapshot.revision"
        :color="snapshot.revision === store.revision ? 'green' : 'gray'"
      >
        <div class="snapshot">
          <span class="snapshot__rev">R{{ snapshot.revision }}</span>
          <a-tag>{{ sourceLabels[snapshot.source] }}</a-tag>
          <span class="snapshot__summary">{{ snapshot.summary }}</span>
          <a-button
            v-if="snapshot.revision !== store.revision"
            size="micro"
            type="link"
            @click="emit('restore', store.history[store.history.length - 1 - index])"
          >
            恢复
          </a-button>
        </div>
      </a-timeline-item>
    </a-timeline>
  </div>
</template>

<style scoped>
.version-history {
  max-height: 320px;
  overflow-y: auto;
  padding: 8px 4px;
}

.snapshot {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.snapshot__rev {
  font-weight: 600;
}
</style>
