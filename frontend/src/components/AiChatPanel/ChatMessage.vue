<script setup lang="ts">
import type { ConversationMessage } from '@/types/workspace';

defineProps<{
  message: ConversationMessage;
}>();
</script>

<template>
  <div class="chat-message" :class="`chat-message--${message.role.toLowerCase()}`">
    <div class="chat-message__role">
      {{ message.role === 'USER' ? '我' : message.role === 'ASSISTANT' ? 'AI助手' : '系统' }}
    </div>
    <div class="chat-message__content">{{ message.content }}</div>
  </div>
</template>

<style scoped>
.chat-message {
  margin-bottom: 12px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.6;
}

.chat-message--user {
  background: #e6f4ff;
}

.chat-message--assistant {
  background: #f6ffed;
}

.chat-message--system {
  background: #f5f5f5;
  color: #888;
}

.chat-message__role {
  font-weight: 600;
  margin-bottom: 4px;
  color: #555;
}
</style>
