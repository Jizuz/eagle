package com.eagle.admin.service;

import com.eagle.admin.entity.ConfigHistory;
import com.eagle.admin.entity.ConfigInfo;
import com.eagle.admin.entity.ConfigPublish;
import com.eagle.admin.mapper.ConfigHistoryMapper;
import com.eagle.admin.mapper.ConfigInfoMapper;
import com.eagle.admin.mapper.ConfigPublishMapper;
import com.eagle.admin.vo.request.ConfigSaveRequest;
import com.eagle.admin.vo.request.ConfigUpdateRequest;
import com.eagle.admin.vo.response.ConfigVO;
import com.eagle.admin.vo.response.PublishVO;
import com.eagle.common.dto.ConfigNodeDTO;
import com.eagle.common.enums.PublishStatus;
import com.eagle.common.exception.EagleException;
import com.eagle.common.result.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 发布服务核心流程单元测试（Mockito）
 */
@ExtendWith(MockitoExtension.class)
class PublishServiceTest {

    @Mock
    private ConfigInfoMapper configInfoMapper;
    @Mock
    private ConfigHistoryMapper configHistoryMapper;
    @Mock
    private ConfigPublishMapper configPublishMapper;
    @Mock
    private ZkSyncService zkSyncService;
    @Mock
    private AlertNotifier alertNotifier;
    @Mock
    private ConfigVoConverter converter;
    @Mock
    private PlatformTransactionManager transactionManager;

    private PublishService publishService;

    @BeforeEach
    void setUp() {
        publishService = new PublishService(configInfoMapper, configHistoryMapper, configPublishMapper,
                zkSyncService, alertNotifier, converter, transactionManager);
    }

    // ==================== 新增 ====================

    @Test
    void saveAndPublishSuccess() throws Exception {
        when(configInfoMapper.selectOne(any())).thenReturn(null);
        when(configInfoMapper.selectIncludeDeleted("dev", "order-service", "switch")).thenReturn(null);
        when(configInfoMapper.insert(any(ConfigInfo.class))).thenAnswer(inv -> {
            inv.getArgument(0, ConfigInfo.class).setId(1L);
            return 1;
        });
        when(configHistoryMapper.selectMaxVersion(1L)).thenReturn(0);
        when(configHistoryMapper.insert(any(ConfigHistory.class))).thenAnswer(inv -> {
            inv.getArgument(0, ConfigHistory.class).setId(100L);
            return 1;
        });
        when(configPublishMapper.insert(any(ConfigPublish.class))).thenAnswer(inv -> {
            inv.getArgument(0, ConfigPublish.class).setId(200L);
            return 1;
        });
        // 发布完成后buildConfigVO会重新查询主表
        when(configInfoMapper.selectById(1L)).thenReturn(new ConfigInfo());
        when(converter.toVO(any(ConfigInfo.class), eq(200L))).thenReturn(new ConfigVO());

        ConfigVO vo = publishService.saveAndPublish(saveRequest());
        assertNotNull(vo);

        // 主表新增
        ArgumentCaptor<ConfigInfo> infoCaptor = ArgumentCaptor.forClass(ConfigInfo.class);
        verify(configInfoMapper).insert(infoCaptor.capture());
        assertEquals("tester", infoCaptor.getValue().getCreatedBy());

        // 版本快照: 版本号 = 最大版本+1
        ArgumentCaptor<ConfigHistory> historyCaptor = ArgumentCaptor.forClass(ConfigHistory.class);
        verify(configHistoryMapper).insert(historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().getItemVersion());
        assertEquals("UPDATE", historyCaptor.getValue().getOperation());
        assertEquals("tester", historyCaptor.getValue().getOperator());

        // 发布记录: 待发布 → ZK同步 → 成功
        ArgumentCaptor<ConfigPublish> publishCaptor = ArgumentCaptor.forClass(ConfigPublish.class);
        verify(configPublishMapper).insert(publishCaptor.capture());
        assertEquals(100L, publishCaptor.getValue().getConfigHistoryId());
        assertEquals(PublishStatus.PENDING.getCode(), publishCaptor.getValue().getPublishStatus());

        verify(zkSyncService).writeNode("dev", "order-service", "switch",
                new ConfigNodeDTO("boolean", "true"));
        verify(configPublishMapper).updateStatus(200L, PublishStatus.SUCCESS.getCode(), null);
    }

    @Test
    void saveAndPublishDuplicateRejected() {
        when(configInfoMapper.selectOne(any())).thenReturn(new ConfigInfo());

        EagleException e = assertThrows(EagleException.class, () -> publishService.saveAndPublish(saveRequest()));
        assertEquals(ErrorCode.CONFIG_EXISTS.getCode(), e.getCode());
        verify(configInfoMapper, never()).insert(any(ConfigInfo.class));
        verifyNoInteractions(zkSyncService);
    }

    @Test
    void saveAndPublishTypeMismatchRejected() {
        ConfigSaveRequest req = saveRequest();
        req.setValueType("number");
        req.setConfigValue("abc");

        EagleException e = assertThrows(EagleException.class, () -> publishService.saveAndPublish(req));
        assertEquals(ErrorCode.TYPE_MISMATCH.getCode(), e.getCode());
        verifyNoInteractions(configInfoMapper, zkSyncService, configPublishMapper);
    }

    @Test
    void saveAndPublishReusesSoftDeletedRow() throws Exception {
        when(configInfoMapper.selectOne(any())).thenReturn(null);
        ConfigInfo deletedRow = new ConfigInfo();
        deletedRow.setId(5L);
        when(configInfoMapper.selectIncludeDeleted("dev", "order-service", "switch")).thenReturn(deletedRow);
        when(configHistoryMapper.selectMaxVersion(5L)).thenReturn(2);
        when(configHistoryMapper.insert(any(ConfigHistory.class))).thenAnswer(inv -> {
            inv.getArgument(0, ConfigHistory.class).setId(101L);
            return 1;
        });
        when(configPublishMapper.insert(any(ConfigPublish.class))).thenAnswer(inv -> {
            inv.getArgument(0, ConfigPublish.class).setId(201L);
            return 1;
        });
        // 发布完成后buildConfigVO会重新查询主表
        when(configInfoMapper.selectById(5L)).thenReturn(new ConfigInfo());
        when(converter.toVO(any(ConfigInfo.class), eq(201L))).thenReturn(new ConfigVO());

        publishService.saveAndPublish(saveRequest());

        // 唯一键被软删行占用 → 复用恢复，不重新insert
        verify(configInfoMapper, never()).insert(any(ConfigInfo.class));
        verify(configInfoMapper).reviveDeleted(any(ConfigInfo.class));
        ArgumentCaptor<ConfigHistory> historyCaptor = ArgumentCaptor.forClass(ConfigHistory.class);
        verify(configHistoryMapper).insert(historyCaptor.capture());
        assertEquals(5L, historyCaptor.getValue().getConfigId());
        assertEquals(3, historyCaptor.getValue().getItemVersion());
        verify(zkSyncService).writeNode(eq("dev"), eq("order-service"), eq("switch"), any(ConfigNodeDTO.class));
    }

    // ==================== 编辑 ====================

    @Test
    void updateAndPublishTypeChangeValidated() {
        // 类型校验先于DB访问，无需stub查询
        ConfigUpdateRequest req = new ConfigUpdateRequest();
        req.setValueType("boolean");
        req.setConfigValue("YES");

        EagleException e = assertThrows(EagleException.class,
                () -> publishService.updateAndPublish(1L, req));
        assertEquals(ErrorCode.TYPE_MISMATCH.getCode(), e.getCode());
        verify(configInfoMapper, never()).updateById(any(ConfigInfo.class));
        verifyNoInteractions(zkSyncService);
    }

    @Test
    void updateAndPublishZkFailureMarksFailedAndAlerts() throws Exception {
        when(configInfoMapper.selectById(1L)).thenReturn(existingConfig());
        when(configHistoryMapper.selectMaxVersion(1L)).thenReturn(1);
        when(configHistoryMapper.insert(any(ConfigHistory.class))).thenAnswer(inv -> {
            inv.getArgument(0, ConfigHistory.class).setId(100L);
            return 1;
        });
        when(configPublishMapper.insert(any(ConfigPublish.class))).thenAnswer(inv -> {
            inv.getArgument(0, ConfigPublish.class).setId(200L);
            return 1;
        });
        when(converter.toVO(any(ConfigInfo.class), eq(200L))).thenReturn(new ConfigVO());
        doThrow(new RuntimeException("zk down")).when(zkSyncService)
                .writeNode(any(), any(), any(), any(ConfigNodeDTO.class));

        ConfigUpdateRequest req = new ConfigUpdateRequest();
        req.setValueType("string");
        req.setConfigValue("new-value");

        // ZK失败: 不抛异常、不回滚DB，仅标记失败+告警
        assertDoesNotThrow(() -> publishService.updateAndPublish(1L, req));
        verify(configPublishMapper).updateStatus(200L, PublishStatus.FAILED.getCode(), "zk down");
        verify(alertNotifier).alert(anyString());
        verify(configPublishMapper, times(1)).updateStatus(anyLong(), anyInt(), any());
    }

    // ==================== 删除 ====================

    @Test
    void deleteAndPublishCreatesDeleteSnapshotAndRemovesZkNode() throws Exception {
        ConfigInfo existing = existingConfig();
        when(configInfoMapper.selectById(1L)).thenReturn(existing);
        when(configHistoryMapper.selectMaxVersion(1L)).thenReturn(3);
        when(configHistoryMapper.insert(any(ConfigHistory.class))).thenAnswer(inv -> {
            inv.getArgument(0, ConfigHistory.class).setId(100L);
            return 1;
        });
        when(configPublishMapper.insert(any(ConfigPublish.class))).thenAnswer(inv -> {
            inv.getArgument(0, ConfigPublish.class).setId(200L);
            return 1;
        });
        when(converter.toVO(any(ConfigInfo.class), eq(200L))).thenReturn(new ConfigVO());

        publishService.deleteAndPublish(1L, "tester");

        verify(configInfoMapper).deleteById(1L);
        ArgumentCaptor<ConfigHistory> historyCaptor = ArgumentCaptor.forClass(ConfigHistory.class);
        verify(configHistoryMapper).insert(historyCaptor.capture());
        assertEquals("DELETE", historyCaptor.getValue().getOperation());
        assertEquals("old-value", historyCaptor.getValue().getConfigValue());
        assertEquals(4, historyCaptor.getValue().getItemVersion());
        verify(zkSyncService).deleteNode("dev", "g", "k");
        verify(configPublishMapper).updateStatus(200L, PublishStatus.SUCCESS.getCode(), null);
    }

    // ==================== 回滚 ====================

    @Test
    void rollbackRejectsHistoryOfOtherConfig() {
        when(configInfoMapper.selectById(1L)).thenReturn(existingConfig());
        ConfigHistory others = new ConfigHistory();
        others.setId(99L);
        others.setConfigId(999L);
        when(configHistoryMapper.selectById(99L)).thenReturn(others);

        EagleException e = assertThrows(EagleException.class,
                () -> publishService.rollback(1L, 99L, "tester"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        verifyNoInteractions(zkSyncService);
    }

    // ==================== 重试 ====================

    @Test
    void retryRejectsNonFailedRecord() {
        ConfigPublish success = new ConfigPublish();
        success.setId(9L);
        success.setPublishStatus(PublishStatus.SUCCESS.getCode());
        when(configPublishMapper.selectById(9L)).thenReturn(success);

        EagleException e = assertThrows(EagleException.class, () -> publishService.retry(9L));
        assertEquals(ErrorCode.STATE_ERROR.getCode(), e.getCode());
        verifyNoInteractions(zkSyncService);
    }

    @Test
    void retrySuccessResyncsZkFromHistorySnapshot() throws Exception {
        ConfigPublish failed = new ConfigPublish();
        failed.setId(9L);
        failed.setEnv("dev");
        failed.setGroupId("g");
        failed.setConfigKey("k");
        failed.setConfigHistoryId(100L);
        failed.setPublishStatus(PublishStatus.FAILED.getCode());
        when(configPublishMapper.selectById(9L)).thenReturn(failed);

        ConfigHistory history = new ConfigHistory();
        history.setId(100L);
        history.setConfigId(1L);
        history.setValueType("string");
        history.setConfigValue("v1");
        history.setOperation("UPDATE");
        history.setItemVersion(2);
        when(configHistoryMapper.selectById(100L)).thenReturn(history);
        when(converter.toPublishVO(any(ConfigPublish.class), eq(history))).thenReturn(new PublishVO());

        PublishVO vo = publishService.retry(9L);
        assertNotNull(vo);
        // 重试使用历史快照值同步ZK
        verify(zkSyncService).writeNode("dev", "g", "k", new ConfigNodeDTO("string", "v1"));
        verify(configPublishMapper).updateStatus(9L, PublishStatus.SUCCESS.getCode(), null);
    }

    // ==================== 测试数据 ====================

    private ConfigSaveRequest saveRequest() {
        ConfigSaveRequest req = new ConfigSaveRequest();
        req.setEnv("dev");
        req.setGroupId("order-service");
        req.setConfigKey("switch");
        req.setValueType("boolean");
        req.setConfigValue("true");
        req.setOperator("tester");
        return req;
    }

    private ConfigInfo existingConfig() {
        ConfigInfo info = new ConfigInfo();
        info.setId(1L);
        info.setEnv("dev");
        info.setGroupId("g");
        info.setConfigKey("k");
        info.setConfigValue("old-value");
        info.setValueType("string");
        return info;
    }
}
