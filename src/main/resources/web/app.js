// noinspection HttpUrlsUsage,JSUnusedLocalSymbols,JSUnusedGlobalSymbols

class WarpManager {
    constructor() {
        this.apiUrl = null;
        this.wsUrl = null;
        this.currentPage = 0;
        this.pageSize = 15;
        this.socket = null;
        this.warps = [];
        this.allWarps = [];
        this.stats = {};
        this.currentFilter = 'all';
        this.searchQuery = ''; // 傳送點名稱搜尋
        this.creatorFilter = 'all'; // 建立者過濾器
        this.worldFilter = 'all'; // 世界過濾器
        this.visibilityFilter = 'all'; // 可見性過濾器// 排序過濾器
        this.currentTab = 'warps'; // 當前分頁
        this.onlineStatus = {}; // 儲存玩家在線狀態
        this.refreshInterval = null; // 自動刷新計時器
        this.settings = this.loadSettings(); // 載入設定
        this.weeklyChart = null; // 週間分析圖表
        this.dimensionChart = null; // 維度分佈圖表
        this.isProcessingPagination = false; // 防止快速點擊分頁按鈕
        this.paginationDebounceTimer = null; // 分頁防抖計時器
        this.tabSwitchDebounceTimer = null; // 分頁切換防抖計時器

        this.init();
    }

    async init() {
        // 應用設定
        this.pageSize = this.settings.pageSize;
        this.applyTheme();

        await this.loadStats(true); // 初始化時取得 apiUrl 和 wsUrl
        await this.loadWarps();
        await this.loadTeleportStats(); // 載入傳送歷史統計
        this.connectWebSocket();
        this.setupEventListeners();
        this.startAutoRefresh(); // 開始自動刷新

        // 確保設定介面正確初始化
        this.initializeSettingsUI();
    }

    async loadStats(isInit = false) {
        try {
            // 直接請求 /api/stats
            const response = await fetch('/api/stats');
            this.stats = await response.json();
            if (isInit) {
                this.apiUrl = this.stats.apiUrl;
                this.wsUrl = this.stats.wsUrl;
            }
            this.updateStatsDisplay();
        } catch (error) {
            console.error('載入統計資料失敗:', error);
        }
    }

    async loadTeleportStats() {
        try {
            const response = await fetch(`${this.apiUrl}/teleport-stats`);
            const teleportStats = await response.json();

            // 同時載入玩家在線狀態
            await this.loadOnlineStatus();

            this.updateTeleportStatsDisplay(teleportStats);
        } catch (error) {
            console.error('載入傳送統計失敗:', error);
        }
    }

    async loadEnhancedStats() {
        try {
            const response = await fetch(`${this.apiUrl}/enhanced-stats`);
            const enhancedStats = await response.json();

            // 儲存統計數據供其他方法使用
            this.enhancedStats = enhancedStats;

            // 同時載入玩家在線狀態
            await this.loadOnlineStatus();

            this.updateEnhancedStatsDisplay(enhancedStats);
            this.updateCharts(enhancedStats);
        } catch (error) {
            console.error('載入增強統計失敗:', error);
            // 清除統計數據
            this.enhancedStats = null;
        }
    }

    async loadOnlineStatus() {
        try {
            const response = await fetch(`${this.apiUrl}/players/online-status`);
            const data = await response.json();
            this.onlineStatus = data.onlineStatus || {};
        } catch (error) {
            console.error('載入玩家狀態失敗:', error);
            this.onlineStatus = {};
        }
    }

    startAutoRefresh() {
        // 清除現有的計時器
        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
        }

        // 只有在設定開啟時才啟動自動刷新
        if (this.settings.autoRefresh) {
            this.refreshInterval = setInterval(async () => {
                try {
                    await this.loadStats();
                    await this.loadWarps();
                    await this.loadTeleportStats();
                    console.log('自動刷新完成');
                } catch (error) {
                    console.error('自動刷新失敗:', error);
                }
            }, 30000); // 30秒間隔
        }
    }

    stopAutoRefresh() {
        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
            this.refreshInterval = null;
        }
    }


    updateTeleportStatsDisplay(teleportStats) {
        // 更新統計卡片（只更新存在的元素）
        const todayTeleportsEl = document.getElementById('today-teleports');
        const totalTeleportsEl = document.getElementById('total-teleports');

        if (todayTeleportsEl) todayTeleportsEl.textContent = teleportStats.todayTeleports || 0;
        if (totalTeleportsEl) totalTeleportsEl.textContent = teleportStats.totalTeleports || 0;

        // 更新熱門傳送點（如果元素存在）
        if (document.getElementById('popular-warps')) {
            this.updatePopularWarps(teleportStats.popularWarps || []);
        }

        // 更新活躍玩家（如果元素存在）
        if (document.getElementById('active-users-list')) {
            this.updateActiveUsers(teleportStats.activeUsers || []);
        }

        // 更新最近傳送記錄（如果元素存在）
        if (document.getElementById('recent-teleports')) {
            this.updateRecentTeleports(teleportStats.recentTeleports || []);
        }
    }

    updatePopularWarps(popularWarps) {
        const container = document.getElementById('popular-warps');
        if (!container) return;

        if (popularWarps.length === 0) {
            container.innerHTML = `
                <div class="text-center text-gray-500 py-8">
                    <i class="fas fa-chart-bar text-3xl mb-3 opacity-50"></i>
                    <div class="text-sm">暫無熱門傳送點數據</div>
                    <div class="text-xs mt-1">開始使用傳送點來查看排行榜</div>
                </div>
            `;
            return;
        }

        // 只顯示前10個熱門傳送點
        const displayWarps = popularWarps.slice(0, 10);
        const maxCount = displayWarps[0]?.usageCount || 1;

        container.innerHTML = displayWarps.map((warp, index) => {
            // 計算使用率百分比
            const usagePercentage = ((warp.usageCount / maxCount) * 100).toFixed(1);

            // 根據排名設置不同的顏色和圖標
            let rankColor, rankIcon, bgGradient, medalEmoji;
            if (index === 0) {
                rankColor = 'bg-gradient-to-r from-yellow-400 to-yellow-500 text-white';
                rankIcon = 'fas fa-crown';
                bgGradient = 'bg-gradient-to-r from-yellow-200 to-amber-200 border-yellow-200 ';
                medalEmoji = '🥇';
            } else if (index === 1) {
                rankColor = 'bg-gradient-to-r from-gray-400 to-gray-500 text-white';
                rankIcon = 'fas fa-medal';
                bgGradient = 'bg-gradient-to-r from-gray-200 to-slate-50 border-gray-200';
                medalEmoji = '🥈';
            } else if (index === 2) {
                rankColor = 'bg-gradient-to-r from-red-400 to-red-600 text-white';
                rankIcon = 'fas fa-award';
                bgGradient = 'bg-gradient-to-r from-red-300 to-amber-50 border-orange-200';
                medalEmoji = '🥉';
            } else {
                rankColor = 'bg-gradient-to-r from-blue-500 to-blue-600 text-white';
                rankIcon = 'fas fa-hashtag';
                bgGradient = 'bg-white border-gray-200 hover:bg-gray-50';
                medalEmoji = '';
            }

            return `
            <div class="flex items-center justify-between p-3 ${bgGradient} rounded-lg border transition-all duration-200 hover:shadow-md group">
                <div class="flex items-center space-x-3 flex-1 min-w-0">
                    <div class="relative flex-shrink-0">
                        <span class="w-8 h-8 ${rankColor} text-xs rounded-full flex items-center justify-center font-bold shadow-sm">
                            ${index < 3 ? `<i class="${rankIcon}"></i>` : index + 1}
                        </span>
                        ${index === 0 ? '<div class="absolute -top-1 -right-1 w-3 h-3 bg-yellow-400 rounded-full animate-ping"></div>' : ''}
                    </div>
                    <div class="flex-1 min-w-0">
                        <div class="flex items-center space-x-2">
                            ${medalEmoji ? `<span class="text-lg">${medalEmoji}</span>` : ''}
                            <span class="font-semibold warp-name-text text-sm truncate" title="${warp.warpName}">${warp.warpName}</span>
                        </div>
                        <div class="flex items-center space-x-2 mt-1">
                            <div class="flex-1 bg-gray-200 rounded-full h-1.5">
                                <div class="bg-gradient-to-r from-blue-500 to-purple-500 h-1.5 rounded-full transition-all duration-300" style="width: ${usagePercentage}%"></div>
                            </div>
                            <span class="text-xs text-gray-500">${usagePercentage}%</span>
                        </div>
                    </div>
                </div>
                <div class="text-right flex-shrink-0 ml-3">
                    <div class="text-lg font-bold warp-count-text">${warp.usageCount}</div>
                    <div class="text-xs warp-desc-text">次使用</div>
                </div>
            </div>
            `;
        }).join('');
    }

    updateActiveUsers(activeUsers) {
        const container = document.getElementById('active-users-list');
        if (!container) return;

        if (activeUsers.length === 0) {
            container.innerHTML = '<div class="text-center text-gray-500 py-4"><i class="fas fa-users text-2xl mb-2"></i><br>暫無數據</div>';
            return;
        }

        // 只顯示前10個活躍玩家
        const displayUsers = activeUsers.slice(0, 10);

        container.innerHTML = displayUsers.map((user, index) => {
            const isOnline = this.onlineStatus[user.playerName] === true;
            const statusColor = isOnline ? 'bg-green-400' : 'bg-red-400';
            const statusTitle = isOnline ? '在線' : '離線';

            // 根據排名設置不同的顏色和圖標
            let rankColor, rankIcon, bgGradient, playerIcon;
            if (index === 0) {
                rankColor = 'bg-gradient-to-r from-purple-500 to-purple-700 text-white';
                rankIcon = 'fas fa-crown';
                bgGradient = 'bg-gradient-to-r from-purple-400 to-indigo-80 border-purple-100';
                playerIcon = 'fas fa-user-crown';
            } else if (index === 1) {
                rankColor = 'bg-gradient-to-r from-blue-500 to-blue-700 text-white';
                rankIcon = 'fas fa-medal';
                bgGradient = 'bg-gradient-to-r from-blue-400 to-cyan-80 border-blue-100';
                playerIcon = 'fas fa-user-tie';
            } else if (index === 2) {
                rankColor = 'bg-gradient-to-r from-green-500 to-green-700 text-white';
                rankIcon = 'fas fa-award';
                bgGradient = 'bg-gradient-to-r from-green-400 to-emerald-80 border-green-100';
                playerIcon = 'fas fa-user-check';
            } else {
                rankColor = 'bg-gradient-to-r from-gray-500 to-gray-600 text-white';
                rankIcon = 'fas fa-hashtag';
                bgGradient = 'bg-white border-gray-200 hover:bg-gray-50';
                playerIcon = 'fas fa-user';
            }

            return `
            <div class="flex items-center justify-between p-4 ${bgGradient} rounded-lg border transition-all duration-200 hover:shadow-md">
                <div class="flex items-center space-x-3">
                    <div class="relative">
                        <span class="w-8 h-8 ${rankColor} text-xs rounded-full flex items-center justify-center font-bold shadow-sm">
                            ${index < 3 ? `<i class="${rankIcon}"></i>` : index + 1}
                        </span>
                        ${index === 0 ? '<div class="absolute -top-1 -right-1 w-3 h-3 bg-purple-400 rounded-full animate-ping"></div>' : ''}
                    </div>
                    <div class="flex items-center space-x-2">
                        <div class="relative">
                            <i class="${playerIcon} text-gray-600"></i>
                            <div class="absolute -bottom-1 -right-1 w-3 h-3 ${statusColor} rounded-full border-2 border-white" title="${statusTitle}"></div>
                        </div>
                        <div>
                            <span class="font-semibold player-name-text text-sm">${user.playerName}</span>
                        </div>
                    </div>
                </div>
                <div class="text-right">
                    <div class="text-lg font-bold player-count-text">${user.teleportCount}</div>
                    <div class="text-xs player-desc-text">次傳送</div>
                </div>
            </div>
            `;
        }).join('');
    }

    updateRecentTeleports(recentTeleports) {
        const tbody = document.getElementById('recent-teleports');
        if (recentTeleports.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="px-4 py-4 text-center text-gray-500">暫無數據</td></tr>';
            return;
        }

        // 只顯示最近15個記錄
        const displayTeleports = recentTeleports.slice(0, 15);

        tbody.innerHTML = displayTeleports.map((teleport, index) => {
            const isOnline = this.onlineStatus[teleport.playerName] === true;
            const statusColor = isOnline ? 'bg-green-400' : 'bg-red-400';
            const statusTitle = isOnline ? '在線' : '離線';

            return `
            <tr class="bg-gray-200">
                <td class="px-4 py-3 whitespace-nowrap text-sm font-medium text-gray-900">
                    <div class="flex items-center">
                        <div class="w-2 h-2 ${statusColor} rounded-full mr-2" title="${statusTitle}"></div>
                        ${teleport.playerName}
                    </div>
                </td>
                <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-500">
                    <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                        ${teleport.warpName}
                    </span>
                </td>
                <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-500">
                    ${this.getWorldDisplayName(teleport.fromWorld)}
                </td>
                <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-500">
                    ${this.getWorldDisplayName(teleport.toWorld)}
                </td>
                <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-500">
                    <div class="text-xs">${teleport.teleportedAt}</div>
                </td>
            </tr>
            `;
        }).join('');
    }

    updateEnhancedStatsDisplay(enhancedStats) {
        // 更新總覽統計卡片
        const enhancedTotalTeleportsEl = document.getElementById('enhanced-total-teleports');
        const enhancedTodayTeleportsEl = document.getElementById('enhanced-today-teleports');
        const enhancedUniquePlayersEl = document.getElementById('enhanced-unique-players');
        const mostPopularWarpCountEl = document.getElementById('most-popular-warp-count');
        const mostPopularWarpNameEl = document.getElementById('most-popular-warp-name');

        if (enhancedTotalTeleportsEl) enhancedTotalTeleportsEl.textContent = enhancedStats.totalTeleports || 0;
        if (enhancedTodayTeleportsEl) enhancedTodayTeleportsEl.textContent = enhancedStats.todayTeleports || 0;
        if (enhancedUniquePlayersEl) enhancedUniquePlayersEl.textContent = enhancedStats.uniquePlayers || 0;

        // 更新熱門傳送點信息
        if (enhancedStats.popularWarps && enhancedStats.popularWarps.length > 0) {
            const topWarp = enhancedStats.popularWarps[0];
            if (mostPopularWarpCountEl) mostPopularWarpCountEl.textContent = topWarp.usageCount;
            if (mostPopularWarpNameEl) {
                mostPopularWarpNameEl.textContent = topWarp.warpName;
                mostPopularWarpNameEl.title = topWarp.warpName; // 添加完整名稱的提示
            }
        } else {
            // 沒有數據時的處理
            if (mostPopularWarpCountEl) mostPopularWarpCountEl.textContent = '0';
            if (mostPopularWarpNameEl) {
                mostPopularWarpNameEl.textContent = '暫無數據';
                mostPopularWarpNameEl.title = '暫無數據';
            }
        }

        // 更新熱門傳送點排行榜
        if (enhancedStats.popularWarps) {
            this.updatePopularWarps(enhancedStats.popularWarps);
        }

        // 更新活躍玩家排行榜
        if (enhancedStats.activeUsers) {
            this.updateActiveUsers(enhancedStats.activeUsers);
        }

        // 更新時段統計
        if (enhancedStats.hourlyStats) {
            this.updateHourlyStats(enhancedStats.hourlyStats);
        }

        // 更新週間統計
        if (enhancedStats.weeklyStats) {
            this.updateWeeklyStats(enhancedStats.weeklyStats);
        }

        // 更新維度分佈統計（從傳送點列表計算）
        this.updateDimensionStatsFromWarps();

        // 更新玩家活躍度統計
        if (enhancedStats.playerActivityStats) {
            this.updatePlayerActivityStats(enhancedStats.playerActivityStats);
        }

        // 更新最近傳送記錄
        if (enhancedStats.recentTeleports) {
            this.updateRecentTeleports(enhancedStats.recentTeleports);
        }

        // 更新效能指標
        if (enhancedStats.performanceStats) {
            this.updatePerformanceStats(enhancedStats.performanceStats);
        }

        // 計算趨勢
        this.calculateTrends(enhancedStats);
    }

    updateHourlyStats(hourlyStats) {
        const container = document.getElementById('hourly-stats');
        if (!container) return;

        if (hourlyStats.length === 0) {
            container.innerHTML = '<div class="text-center text-gray-500 py-4">暫無數據</div>';
            return;
        }

        // 找出最高值用於計算百分比
        const maxCount = Math.max(...hourlyStats.map(stat => stat.count));

        container.innerHTML = hourlyStats.map(stat => {
            const percentage = maxCount > 0 ? (stat.count / maxCount) * 100 : 0;
            const hour = parseInt(stat.hour);
            const timeLabel = `${hour.toString().padStart(2, '0')}:00`;

            return `
                <div class="flex items-center justify-between py-1">
                    <span class="text-xs text-gray-600 w-12">${timeLabel}</span>
                    <div class="flex-1 mx-2">
                        <div class="bg-gray-200 rounded-full h-2">
                            <div class="bg-indigo-500 h-2 rounded-full transition-all duration-300" style="width: ${percentage}%"></div>
                        </div>
                    </div>
                    <span class="text-xs font-semibold text-gray-800 w-8 text-right">${stat.count}</span>
                </div>
            `;
        }).join('');
    }

    updateWeeklyStats(weeklyStats) {
        const chartContainer = document.getElementById('weekly-chart-container');
        if (!chartContainer) return;

        if (weeklyStats.length === 0) {
            chartContainer.innerHTML = '<div class="text-center text-gray-500 py-4 h-64 flex items-center justify-center">暫無數據</div>';
            return;
        }

        // 確保有畫布元素
        let canvas = document.getElementById('weekly-chart');
        if (!canvas) {
            chartContainer.innerHTML = '<canvas id="weekly-chart"></canvas>';
            canvas = document.getElementById('weekly-chart');
        }

        // 銷毀現有圖表
        if (this.weeklyChart) {
            this.weeklyChart.destroy();
        }

        // 準備數據 - 確保按星期順序排列
        const dayOrder = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'];
        const sortedStats = dayOrder.map(dayName => {
            const stat = weeklyStats.find(s => s.dayName === dayName);
            return {
                dayName: dayName,
                count: stat ? stat.count : 0
            };
        });

        const labels = sortedStats.map(stat => stat.dayName);
        const data = sortedStats.map(stat => stat.count);

        // 判斷當前主題
        const isDarkTheme = document.body.classList.contains('dark-theme');
        const textColor = isDarkTheme ? '#f1f5f9' : '#374151';
        const gridColor = isDarkTheme ? 'rgba(148, 163, 184, 0.3)' : 'rgba(156, 163, 175, 0.3)';

        // 創建折線圖
        this.weeklyChart = new Chart(canvas, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [{
                    label: '傳送次數',
                    data: data,
                    borderColor: '#14b8a6',
                    backgroundColor: 'rgba(20, 184, 166, 0.1)',
                    borderWidth: 3,
                    fill: true,
                    tension: 0.4,
                    pointBackgroundColor: '#14b8a6',
                    pointBorderColor: '#ffffff',
                    pointBorderWidth: 2,
                    pointRadius: 6,
                    pointHoverRadius: 8,
                    pointHoverBackgroundColor: '#0891b2',
                    pointHoverBorderColor: '#ffffff',
                    pointHoverBorderWidth: 3
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: false
                    },
                    tooltip: {
                        backgroundColor: isDarkTheme ? 'rgba(30, 41, 59, 0.9)' : 'rgba(255, 255, 255, 0.9)',
                        titleColor: textColor,
                        bodyColor: textColor,
                        borderColor: '#14b8a6',
                        borderWidth: 1,
                        cornerRadius: 8,
                        displayColors: false,
                        callbacks: {
                            title: function(context) {
                                return context[0].label;
                            },
                            label: function(context) {
                                return `傳送次數: ${context.parsed.y}`;
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        grid: {
                            color: gridColor,
                            drawBorder: false
                        },
                        ticks: {
                            color: textColor,
                            font: {
                                size: 12
                            }
                        }
                    },
                    y: {
                        beginAtZero: true,
                        grid: {
                            color: gridColor,
                            drawBorder: false
                        },
                        ticks: {
                            color: textColor,
                            font: {
                                size: 12
                            },
                            stepSize: 1
                        }
                    }
                },
                interaction: {
                    intersect: false,
                    mode: 'index'
                },
                elements: {
                    point: {
                        hoverRadius: 8
                    }
                }
            }
        });
    }

    updateDimensionStats(dimensionStats) {
        const chartContainer = document.getElementById('dimension-stats');
        const legendContainer = document.getElementById('dimension-legend');
        
        if (!chartContainer || !legendContainer) {
            console.warn('維度統計容器元素未找到');
            return;
        }

        console.log('維度統計數據:', dimensionStats);

        // 清理現有內容
        this.destroyDimensionChart();
        
        // 檢查數據有效性
        if (!dimensionStats || !Array.isArray(dimensionStats) || dimensionStats.length === 0) {
            this.showNoDimensionData(chartContainer, legendContainer);
            return;
        }

        // 驗證數據結構
        const validStats = dimensionStats.filter(stat => 
            stat && 
            typeof stat.dimension === 'string' && 
            typeof stat.count === 'number' && 
            stat.count > 0
        );

        if (validStats.length === 0) {
            console.warn('沒有有效的維度統計數據');
            this.showNoDimensionData(chartContainer, legendContainer);
            return;
        }

        // 創建圓餅圖
        this.createDimensionPieChart(validStats, chartContainer, legendContainer);
    }

    destroyDimensionChart() {
        if (this.dimensionChart) {
            this.dimensionChart.destroy();
            this.dimensionChart = null;
        }
    }

    showNoDimensionData(chartContainer, legendContainer) {
        chartContainer.innerHTML = `
            <div class="text-center text-gray-500 py-8 h-64 flex flex-col items-center justify-center">
                <i class="fas fa-globe text-4xl mb-3 opacity-50"></i>
                <div class="text-sm font-medium">暫無維度數據</div>
                <div class="text-xs mt-1 opacity-75">請先創建一些傳送點</div>
            </div>
        `;
        legendContainer.innerHTML = '';
    }

    createDimensionPieChart(dimensionStats, chartContainer, legendContainer) {
        // 銷毀現有圖表（如果存在）
        if (this.dimensionChart) {
            this.dimensionChart.destroy();
            this.dimensionChart = null;
        }
        
        // 準備畫布
        chartContainer.innerHTML = '<canvas id="dimension-chart" style="max-height: 256px;"></canvas>';
        
        // 等待DOM更新
        setTimeout(() => {
            const canvas = document.getElementById('dimension-chart');
            
            if (!canvas) {
                console.error('無法創建畫布元素');
                this.showChartError(chartContainer);
                return;
            }

            // 處理數據
            const chartData = this.prepareDimensionChartData(dimensionStats);
            console.log('處理後的圖表數據:', chartData);

            // 驗證數據
            if (!chartData.data || chartData.data.length === 0) {
                console.error('圖表數據無效');
                this.showChartError(chartContainer);
                return;
            }

            // 創建圖表配置
            const chartConfig = this.createDimensionChartConfig(chartData);

            if (!chartConfig) {
                console.error('圖表配置創建失敗');
                this.showChartError(chartContainer);
                return;
            }

            try {
                // 創建圓餅圖
                this.dimensionChart = new Chart(canvas, chartConfig);
                
                // 創建圖例
                this.createDimensionLegend(dimensionStats, chartData.total, legendContainer);
                
                console.log('維度圓餅圖創建成功');
            } catch (error) {
                console.error('創建維度圓餅圖失敗:', error);
                this.showChartError(chartContainer);
            }
        }, 100);
    }

    prepareDimensionChartData(dimensionStats) {
        const labels = [];
        const data = [];
        const colors = [];

        // 預定義維度顏色
        const dimensionColorMap = {
            'world': { bg: '#22c55e', border: '#16a34a' },           // 主世界 - 綠色
            'world_nether': { bg: '#ef4444', border: '#dc2626' },   // 地獄 - 紅色  
            'world_the_end': { bg: '#8b5cf6', border: '#7c3aed' },  // 終界 - 紫色
        };

        // 默認顏色序列
        const defaultColors = [
            { bg: '#3b82f6', border: '#2563eb' }, // 藍色
            { bg: '#f59e0b', border: '#d97706' }, // 橙色
            { bg: '#ec4899', border: '#db2777' }, // 粉色
            { bg: '#10b981', border: '#059669' }, // 翠綠色
        ];

        // 確保數據是數組且不為空
        if (!Array.isArray(dimensionStats) || dimensionStats.length === 0) {
            console.error('維度統計數據無效或為空');
            return { labels: [], data: [], colors: [], total: 0 };
        }

        dimensionStats.forEach((stat, index) => {
            // 驗證每個統計項目
            if (!stat || typeof stat.dimension !== 'string' || typeof stat.count !== 'number') {
                console.warn('跳過無效的統計項目:', stat);
                return;
            }

            labels.push(this.getDimensionDisplayName(stat.dimension));
            data.push(Math.max(0, stat.count)); // 確保數值不為負數
            
            // 選擇顏色
            const color = dimensionColorMap[stat.dimension] || defaultColors[index % defaultColors.length];
            colors.push(color);
        });

        const total = data.reduce((sum, count) => sum + count, 0);

        // 驗證最終數據
        if (labels.length === 0 || data.length === 0 || total === 0) {
            console.error('處理後的圖表數據為空');
            return { labels: [], data: [], colors: [], total: 0 };
        }

        return { labels, data, colors, total };
    }

    createDimensionChartConfig(chartData) {
        const isDarkTheme = document.body.classList.contains('dark-theme');
        
        // 驗證圖表數據
        if (!chartData || !chartData.labels || !chartData.data || !chartData.colors) {
            console.error('圖表數據結構無效');
            return null;
        }

        // 確保數據數組長度一致
        if (chartData.labels.length !== chartData.data.length || 
            chartData.data.length !== chartData.colors.length) {
            console.error('圖表數據數組長度不一致');
            return null;
        }

        return {
            type: 'pie',
            data: {
                labels: chartData.labels,
                datasets: [{
                    data: chartData.data,
                    backgroundColor: chartData.colors.map(c => c && c.bg ? c.bg : '#6b7280'),
                    borderColor: chartData.colors.map(c => c && c.border ? c.border : '#4b5563'),
                    borderWidth: 2,
                    hoverBorderWidth: 3,
                    hoverOffset: 8
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        backgroundColor: isDarkTheme ? 'rgba(15, 23, 42, 0.95)' : 'rgba(255, 255, 255, 0.95)',
                        titleColor: isDarkTheme ? '#f1f5f9' : '#1f2937',
                        bodyColor: isDarkTheme ? '#e2e8f0' : '#374151',
                        borderColor: isDarkTheme ? '#475569' : '#d1d5db',
                        borderWidth: 1,
                        cornerRadius: 8,
                        padding: 12,
                        callbacks: {
                            label: (context) => {
                                try {
                                    const total = chartData.total || context.dataset.data.reduce((a, b) => a + b, 0);
                                    const percentage = total > 0 ? ((context.parsed / total) * 100).toFixed(1) : '0.0';
                                    return `${context.label}: ${context.parsed} 個傳送點 (${percentage}%)`;
                                } catch (error) {
                                    console.error('工具提示計算錯誤:', error);
                                    return `${context.label}: ${context.parsed} 個傳送點`;
                                }
                            }
                        }
                    }
                },
                animation: {
                    animateRotate: true,
                    duration: 800,
                    easing: 'easeOutQuart'
                }
            }
        };
    }

    createDimensionLegend(dimensionStats, total, legendContainer) {
        const isDarkTheme = document.body.classList.contains('dark-theme');

        legendContainer.innerHTML = dimensionStats.map((stat, index) => {
            const percentage = ((stat.count / total) * 100).toFixed(1);
            const dimensionColorMap = {
                'world': {bg: '#22c55e', border: '#16a34a'},
                'world_nether': {bg: '#ef4444', border: '#dc2626'},
                'world_the_end': {bg: '#8b5cf6', border: '#7c3aed'},
            };
            const defaultColors = [
                {bg: '#3b82f6', border: '#2563eb'},
                {bg: '#f59e0b', border: '#d97706'},
                {bg: '#ec4899', border: '#db2777'},
                {bg: '#10b981', border: '#059669'},
            ];

            const color = dimensionColorMap[stat.dimension] || defaultColors[index % defaultColors.length];

            return `
                <div class="flex items-center justify-between p-3 rounded-lg hover:bg-gray-50 transition-all duration-200 group">
                    <div class="flex items-center space-x-3">
                        <div class="w-4 h-4 rounded-full shadow-sm" 
                             style="background: ${color.bg}; border: 2px solid ${color.border};"></div>
                        <span class="text-sm font-medium text-gray-700 group-hover:text-gray-900">
                            ${this.getDimensionDisplayName(stat.dimension)}
                        </span>
                    </div>
                    <div class="text-right">
                        <div class="text-sm font-bold text-gray-800">${stat.count} 個</div>
                        <div class="text-xs text-gray-500">${percentage}%</div>
                    </div>
                </div>
            `;
        }).join('');
    }

    showChartError(chartContainer) {
        chartContainer.innerHTML = `
            <div class="text-center text-red-500 py-8 h-64 flex flex-col items-center justify-center">
                <i class="fas fa-exclamation-triangle text-4xl mb-3"></i>
                <div class="text-sm font-medium">圖表載入失敗</div>
                <div class="text-xs mt-1">請重新整理頁面</div>
            </div>
        `;
    }

    updateDimensionStatsFromWarps() {
        // 從現有的傳送點數據計算維度分佈
        if (!this.allWarps || this.allWarps.length === 0) {
            console.log('沒有傳送點數據，跳過維度統計');
            this.updateDimensionStats([]);
            return;
        }

        // 統計各維度的傳送點數量
        const dimensionCounts = {};
        this.allWarps.forEach(warp => {
            const world = warp.world || 'unknown';
            dimensionCounts[world] = (dimensionCounts[world] || 0) + 1;
        });

        // 轉換為統計格式
        const dimensionStats = Object.entries(dimensionCounts)
            .map(([dimension, count]) => ({ dimension, count }))
            .sort((a, b) => b.count - a.count); // 按數量降序排列

        console.log('從傳送點列表計算的維度統計:', dimensionStats);
        this.updateDimensionStats(dimensionStats);
    }

    getDimensionDisplayName(dimension) {
        const dimensionNames = {
            'world': '主世界',
            'world_nether': '地獄',
            'world_the_end': '終界'
        };
        return dimensionNames[dimension] || `🗺️ ${dimension}`;
    }

    updatePlayerActivityStats(playerActivityStats) {
        const avgDailyTeleportsEl = document.getElementById('avg-daily-teleports');
        const weekActivePlayersEl = document.getElementById('week-active-players');
        const monthActivePlayersEl = document.getElementById('month-active-players');
        const todayAvgComparisonEl = document.getElementById('today-avg-comparison');
        const playersActivityRateEl = document.getElementById('players-activity-rate');

        if (avgDailyTeleportsEl) {
            avgDailyTeleportsEl.textContent = playerActivityStats.avgDailyTeleports || 0;
        }
        if (weekActivePlayersEl) {
            weekActivePlayersEl.textContent = playerActivityStats.weekActivePlayers || 0;
        }
        if (monthActivePlayersEl) {
            monthActivePlayersEl.textContent = playerActivityStats.monthActivePlayers || 0;
        }
        if (todayAvgComparisonEl) {
            const avg = playerActivityStats.avgDailyTeleports || 0;
            const today = playerActivityStats.todayTeleports || 0;
            if (avg > 0) {
                const comparison = ((today / avg - 1) * 100).toFixed(1);
                todayAvgComparisonEl.textContent = comparison > 0 ? `+${comparison}%` : `${comparison}%`;
            } else {
                todayAvgComparisonEl.textContent = '平均值';
            }
        }
        if (playersActivityRateEl) {
            playersActivityRateEl.textContent = `${playerActivityStats.weekActivePlayers || 0} 人活躍`;
        }
    }

    updatePerformanceStats(performanceStats) {
        const avgResponseTimeEl = document.getElementById('avg-response-time');
        const totalRecordsEl = document.getElementById('total-records');
        const successRateEl = document.getElementById('success-rate');

        if (avgResponseTimeEl) {
            avgResponseTimeEl.textContent = performanceStats.avgResponseTime || '< 1ms';
        }
        if (totalRecordsEl) {
            totalRecordsEl.textContent = performanceStats.totalRecords || 0;
        }
        if (successRateEl) {
            successRateEl.textContent = performanceStats.successRate || '100%';
        }
    }

    updateCharts(enhancedStats) {
        // 圖表功能已移除
    }



    calculateTrends(enhancedStats) {
        // 計算本週趨勢
        const totalTeleportsTrendEl = document.getElementById('total-teleports-trend');
        if (totalTeleportsTrendEl && enhancedStats.weekTeleports && enhancedStats.totalTeleports) {
            const weekPercentage = ((enhancedStats.weekTeleports / enhancedStats.totalTeleports) * 100).toFixed(1);
            totalTeleportsTrendEl.textContent = `+${weekPercentage}%`;
        }
    }

    getWorldDisplayName(worldName) {
        // 處理 null 或 undefined
        if (!worldName) {
            return '未知世界';
        }
        
        const worldNames = {
            'world': '主世界',
            'world_nether': '地獄',
            'world_the_end': '終界',
            'DIM-1': '地獄',
            'DIM1': '終界'
        };
        
        return worldNames[worldName] || worldName;
    }

    updateNoResultsMessage() {
        const emptyStateDiv = document.getElementById('empty-state');
        if (!emptyStateDiv) return;

        let message;

        if (this.searchQuery.trim()) {
            message = `
                <i class="fas fa-search text-6xl text-gray-400 mb-4"></i>
                <h3 class="text-xl font-semibold text-gray-700 mb-2">找不到符合「${this.searchQuery}」的傳送點</h3>
                <p class="text-gray-600">請嘗試使用其他關鍵字</p>
            `;
        } else {
            message = `
                <i class="fas fa-map-marked-alt text-6xl text-gray-400 mb-4"></i>
                <h3 class="text-xl font-semibold text-gray-700 mb-2">尚無傳送點</h3>
                <p class="text-gray-600">建立您的第一個傳送點開始使用</p>
            `;
        }

        emptyStateDiv.innerHTML = `<div class="text-center py-12">${message}</div>`;
    }

    // 處理分頁點擊事件（快速響應）
    handlePaginationClick(targetPage, button) {
        // 如果正在處理分頁請求，忽略新的點擊
        if (this.isProcessingPagination) {
            console.log('分頁處理中，忽略點擊');
            return;
        }

        // 設置處理狀態
        this.isProcessingPagination = true;
        
        try {
            console.log(`切換到第 ${targetPage + 1} 頁`);
            this.currentPage = targetPage;
            this.applyFilter();
            
        } catch (error) {
            console.error('分頁切換失敗:', error);
            this.showNotification('分頁切換失敗', 'error');
        } finally {
            // 立即恢復狀態
            this.isProcessingPagination = false;
            console.log('分頁處理完成');
        }
    }

    // 禁用/啟用所有分頁按鈕
    disablePaginationButtons(disable) {
        const paginationContainer = document.getElementById('pagination');
        if (paginationContainer) {
            const buttons = paginationContainer.querySelectorAll('button');
            buttons.forEach(btn => {
                btn.disabled = disable;
                if (disable) {
                    btn.classList.add('opacity-50', 'cursor-not-allowed');
                } else {
                    btn.classList.remove('opacity-50', 'cursor-not-allowed');
                }
            });
        }
    }

    // 更新分頁顯示
    updatePagination(paginationInfo) {
        const paginationContainer = document.getElementById('pagination');
        const pageInfoContainer = document.getElementById('page-info');
        
        if (!paginationContainer) return;

        const { currentPage, totalPages, totalElements, pageSize } = paginationInfo;
        
        // 更新頁面信息
        if (pageInfoContainer) {
            const start = totalElements === 0 ? 0 : currentPage * pageSize + 1;
            const end = Math.min((currentPage + 1) * pageSize, totalElements);
            pageInfoContainer.innerHTML = `顯示 ${start}-${end} 項，共 ${totalElements} 項`;
        }

        // 清空分頁容器
        paginationContainer.innerHTML = '';

        if (totalPages <= 1) {
            return; // 只有一頁或沒有數據時不顯示分頁
        }

        // 創建分頁按鈕
        const createButton = (text, page, disabled = false, active = false) => {
            const button = document.createElement('button');
            button.className = `px-3 py-2 text-sm font-medium rounded-lg transition-colors duration-200 ${
                disabled 
                    ? 'bg-gray-100 text-gray-400 cursor-not-allowed' 
                    : active 
                        ? 'bg-blue-600 text-white' 
                        : 'bg-white text-gray-700 hover:bg-gray-50 border border-gray-300'
            }`;
            button.textContent = text;
            button.disabled = disabled;
            
            // 添加數據屬性用於調試
            button.setAttribute('data-page', page.toString());
            button.setAttribute('data-active', active.toString());
            
            if (!disabled && !active) {
                button.addEventListener('click', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    
                    // 防止重複點擊
                    if (button.disabled || this.isProcessingPagination) {
                        console.log('按鈕已禁用或正在處理中，忽略點擊');
                        return;
                    }
                    
                    this.handlePaginationClick(page, button);
                });
            }
            
            return button;
        };

        // 上一頁按鈕
        const prevButton = createButton('‹ 上一頁', currentPage - 1, currentPage === 0);
        paginationContainer.appendChild(prevButton);

        // 頁碼按鈕
        const startPage = Math.max(0, currentPage - 2);
        const endPage = Math.min(totalPages - 1, currentPage + 2);

        // 如果不是從第一頁開始，添加第一頁和省略號
        if (startPage > 0) {
            paginationContainer.appendChild(createButton('1', 0));
            if (startPage > 1) {
                const ellipsis = document.createElement('span');
                ellipsis.className = 'px-3 py-2 text-gray-500';
                ellipsis.textContent = '...';
                paginationContainer.appendChild(ellipsis);
            }
        }

        // 添加頁碼按鈕
        for (let i = startPage; i <= endPage; i++) {
            const button = createButton((i + 1).toString(), i, false, i === currentPage);
            paginationContainer.appendChild(button);
        }

        // 如果不是到最後一頁，添加省略號和最後一頁
        if (endPage < totalPages - 1) {
            if (endPage < totalPages - 2) {
                const ellipsis = document.createElement('span');
                ellipsis.className = 'px-3 py-2 text-gray-500';
                ellipsis.textContent = '...';
                paginationContainer.appendChild(ellipsis);
            }
            paginationContainer.appendChild(createButton(totalPages.toString(), totalPages - 1));
        }

        // 下一頁按鈕
        const nextButton = createButton('下一頁 ›', currentPage + 1, currentPage >= totalPages - 1);
        paginationContainer.appendChild(nextButton);
    }

    // 清除所有過濾器
    async clearAllFilters() {
        this.searchQuery = '';
        this.visibilityFilter = 'all';
        this.worldFilter = 'all';
        this.creatorFilter = 'all';
        this.currentPage = 0;

        // 重置 UI 元素
        const searchInput = document.getElementById('search-input');
        const visibilityFilter = document.getElementById('visibility-filter');
        const worldFilter = document.getElementById('world-filter');

        if (searchInput && searchInput instanceof HTMLInputElement) searchInput.value = '';
        if (visibilityFilter && visibilityFilter instanceof HTMLSelectElement) visibilityFilter.value = 'all';
        if (worldFilter && worldFilter instanceof HTMLSelectElement) worldFilter.value = 'all';

        // 重新應用過濾器
        this.applyFilter();
        
        this.showNotification('已清除所有過濾條件', 'info');
    }

    // 檢查是否有活躍的過濾器
    hasActiveFilters() {
        return this.searchQuery.trim() !== '' || 
               this.visibilityFilter !== 'all' || 
               this.worldFilter !== 'all' || 
               this.creatorFilter !== 'all';
    }

    // 更新世界過濾器選項
    updateWorldFilterOptions() {
        const worldFilter = document.getElementById('world-filter');
        if (!worldFilter) return;

        // 獲取所有唯一的世界名稱
        const worlds = [...new Set(this.allWarps.map(warp => warp.world))].sort();
        
        // 保存當前選擇的值
        const currentValue = (worldFilter instanceof HTMLSelectElement) ? worldFilter.value : 'all';
        
        // 清空並重新填充選項
        worldFilter.innerHTML = '<option value="all">所有世界</option>';
        
        worlds.forEach(world => {
            const option = document.createElement('option');
            option.value = world;
            option.textContent = this.getWorldDisplayName(world);
            worldFilter.appendChild(option);
        });
        
        // 恢復之前的選擇（如果仍然有效）
        if (worldFilter instanceof HTMLSelectElement) {
            if (worlds.includes(currentValue)) {
                worldFilter.value = currentValue;
            } else {
                worldFilter.value = 'all';
                this.worldFilter = 'all';
            }
        }
    }

    async loadWarps() {
        try {
            const loadingEl = document.getElementById('loading');
            const warpsGridEl = document.getElementById('warps-grid');
            const emptyStateEl = document.getElementById('empty-state');

            if (loadingEl) loadingEl.classList.remove('hidden');
            if (warpsGridEl) warpsGridEl.classList.add('hidden');
            if (emptyStateEl) emptyStateEl.classList.add('hidden');

            // 用 apiUrl 取得傳送點
            const response = await fetch(`${this.apiUrl}/warps?page=0&size=1000`);
            const data = await response.json();

            this.allWarps = data.warps;
            this.updateWorldFilterOptions(); // 更新世界過濾器選項
            this.applyFilter();
            
            // 如果在統計頁面，更新維度統計
            if (this.currentTab === 'stats') {
                this.updateDimensionStatsFromWarps();
            }

            if (loadingEl) loadingEl.classList.add('hidden');
            if (warpsGridEl) warpsGridEl.classList.remove('hidden');
        } catch (error) {
            console.error('載入傳送點失敗:', error);
            const loadingEl = document.getElementById('loading');
            if (loadingEl) {
                loadingEl.innerHTML = `
                    <i class="fas fa-exclamation-triangle text-2xl text-red-500"></i>
                    <p class="text-red-600 mt-2">載入失敗: ${error.message}</p>
                `;
            }
        }
    }
    applyFilter() {
        console.log(`應用過濾器 - 當前頁: ${this.currentPage + 1}`);
        console.log(`過濾器狀態:`, {
            visibilityFilter: this.visibilityFilter,
            worldFilter: this.worldFilter,
            creatorFilter: this.creatorFilter,
            searchQuery: this.searchQuery,
            totalWarps: this.allWarps.length
        });

        let filteredWarps = this.allWarps;

        // 1. 根據可見性過濾 (使用 visibilityFilter 而不是 currentFilter)
        switch (this.visibilityFilter) {
            case 'public':
                filteredWarps = filteredWarps.filter(warp => !warp.isPrivate);
                break;
            case 'private':
                filteredWarps = filteredWarps.filter(warp => warp.isPrivate);
                break;
            case 'all':
            default:
                // 保持所有傳送點
                break;
        }

        // 2. 根據傳送點名稱搜尋過濾
        if (this.searchQuery.trim()) {
            const query = this.searchQuery.toLowerCase().trim();
            filteredWarps = filteredWarps.filter(warp => {
                const name = warp.name.toLowerCase();
                return name.includes(query);
            });
        }

        // 3. 根據建立者過濾
        if (this.creatorFilter !== 'all') {
            filteredWarps = filteredWarps.filter(warp => warp.creator === this.creatorFilter);
        }

        // 4. 根據世界過濾
        if (this.worldFilter !== 'all') {
            filteredWarps = filteredWarps.filter(warp => warp.world === this.worldFilter);
        }

        const totalElements = filteredWarps.length;
        console.log(`過濾結果: ${totalElements} 個傳送點符合條件`);
        const totalPages = Math.ceil(totalElements / this.pageSize);

        if (this.currentPage >= totalPages && totalPages > 0) {
            this.currentPage = totalPages - 1;
        } else if (this.currentPage < 0) {
            this.currentPage = 0;
        }

        const start = this.currentPage * this.pageSize;
        const end = Math.min(start + this.pageSize, totalElements);
        this.warps = filteredWarps.slice(start, end);

        // 更新過濾統計
        this.updateFilterStats(totalElements, this.allWarps.length);

        this.updateWarpsDisplay();
        this.updatePagination({
            currentPage: this.currentPage,
            totalPages: totalPages,
            totalElements: totalElements,
            pageSize: this.pageSize
        });

        const warpsGridEl = document.getElementById('warps-grid');
        const emptyStateEl = document.getElementById('empty-state');

        if (filteredWarps.length === 0) {
            if (warpsGridEl) warpsGridEl.classList.add('hidden');
            if (emptyStateEl) emptyStateEl.classList.remove('hidden');
            // 更新無結果訊息
            this.updateNoResultsMessage();
        } else {
            if (warpsGridEl) warpsGridEl.classList.remove('hidden');
            if (emptyStateEl) emptyStateEl.classList.add('hidden');
        }
    }

    updateStatsDisplay() {
        const totalWarps = this.stats.totalWarps || 0;
        const publicWarps = this.stats.publicWarps || 0;
        const privateWarps = this.stats.privateWarps || 0;

        document.getElementById('total-warps').textContent = totalWarps;
        document.getElementById('public-warps').textContent = publicWarps;
        document.getElementById('private-warps').textContent = privateWarps;

        // 更新詳細統計頁面的元素（如果存在）
        const totalUsersEl = document.getElementById('total-users');
        const activeUsersEl = document.getElementById('active-users');
        const todayTeleportsEl = document.getElementById('today-teleports');
        const totalTeleportsEl = document.getElementById('total-teleports');

        if (totalUsersEl) totalUsersEl.textContent = this.stats.totalUsers || 0;
        if (activeUsersEl) activeUsersEl.textContent = this.stats.activeUsers || 0;
        if (todayTeleportsEl) todayTeleportsEl.textContent = this.stats.todayTeleports || 0;
        if (totalTeleportsEl) totalTeleportsEl.textContent = this.stats.totalTeleports || 0;

        this.updateStatsHighlight();
    }

    updateStatsHighlight() {
        const cards = document.querySelectorAll('#stats > div');
        
        // 清除所有卡片的高亮效果
        cards.forEach(card => {
            card.classList.remove('ring-2', 'ring-blue-500', 'ring-green-500', 'ring-amber-500');
        });

        // 只有在有活躍過濾器時才添加高亮
        if (this.visibilityFilter !== 'all') {
            let targetCard = null;
            switch (this.visibilityFilter) {
                case 'public':
                    targetCard = cards[1];
                    break;
                case 'private':
                    targetCard = cards[2];
                    break;
            }
            
            // 添加高亮效果
            if (targetCard) {
                targetCard.classList.add('ring-2', 'ring-blue-500');
            }
        }
    }

    updateWarpsDisplay() {
        const container = document.getElementById('warps-grid');
        container.innerHTML = '';

        // 使用已經分頁的 warps 數據，而不是重新過濾
        this.warps.forEach(warp => {
            const warpCard = this.createWarpCard(warp);
            container.appendChild(warpCard);
        });

        // 顯示空狀態或隱藏
        const emptyState = document.getElementById('empty-state');
        if (this.warps.length === 0) {
            emptyState.classList.remove('hidden');
            if (this.hasActiveFilters()) {
                emptyState.innerHTML = `
                    <i class="fas fa-search text-6xl text-gray-400 mb-4"></i>
                    <h3 class="text-xl font-semibold text-gray-700 mb-2">找不到符合條件的傳送點</h3>
                    <p class="text-gray-600">請嘗試調整過濾條件</p>
                `;
            } else {
                emptyState.innerHTML = `
                    <i class="fas fa-map-marked-alt text-6xl text-gray-400 mb-4"></i>
                    <h3 class="text-xl font-semibold text-gray-700 mb-2">尚無傳送點</h3>
                    <p class="text-gray-600">建立您的第一個傳送點開始使用</p>
                `;
            }
        } else {
            emptyState.classList.add('hidden');
        }
    }

    createWarpCard(warp) {
        const card = document.createElement('div');

        // 根據傳送點類型設置不同的樣式
        const isPrivate = warp.isPrivate;
        const cardStyle = isPrivate
            ? 'bg-gradient-to-br from-amber-50 to-orange-50 border-amber-200 hover:from-amber-100 hover:to-orange-100'
            : 'bg-gradient-to-br from-emerald-50 to-teal-50 border-emerald-200 hover:from-emerald-100 hover:to-teal-100';

        card.className = `${cardStyle} rounded-xl p-5 border-2 hover:shadow-lg transition-all duration-300 transform hover:-translate-y-1`;

        const visibilityConfig = isPrivate
            ? {
                color: 'text-amber-700 bg-gradient-to-r from-amber-100 to-yellow-100 border-amber-300',
                icon: 'fa-lock',
                badge: '🔒 私人'
            }
            : {
                color: 'text-emerald-700 bg-gradient-to-r from-emerald-100 to-teal-100 border-emerald-300',
                icon: 'fa-globe',
                badge: '🌍 公共'
            };

        // 世界名稱美化
        const worldDisplayNames = {
            'world': '主世界',
            'world_nether': '地獄',
            'world_the_end': '終界'
        };
        const worldDisplay = worldDisplayNames[warp.world] || `🗺️ ${warp.world}`;

        let invitedPlayersHtml = '';
        if (isPrivate && warp.invitedPlayers && warp.invitedPlayers.length > 0) {
            invitedPlayersHtml = `
                <div class="mt-4 p-3 bg-white/60 rounded-lg border border-purple-200">
                    <div class="flex items-center text-xs text-purple-700 font-semibold mb-2">
                        <i class="fas fa-users mr-1"></i>
                        已邀請玩家 (${warp.invitedPlayers.length})
                    </div>
                    <div class="flex flex-wrap gap-1">
                        ${warp.invitedPlayers.map(player =>
                `<span class="inline-flex items-center text-xs bg-gradient-to-r from-purple-100 to-indigo-100 text-purple-800 px-2 py-1 rounded-full border border-purple-200 font-medium">
                    <i class="fas fa-user-friends mr-1"></i>${player}
                </span>`
            ).join('')}
                    </div>
                </div>
            `;
        }

        card.innerHTML = `
            <!-- 標題區域 -->
            <div class="flex items-start justify-between mb-4">
                <div class="flex items-center space-x-2">
                    <div class="w-10 h-10 bg-gradient-to-br ${isPrivate ? 'from-amber-400 to-orange-500' : 'from-emerald-400 to-teal-500'} rounded-lg flex items-center justify-center shadow-md">
                        <i class="fas fa-map-marker-alt ${isPrivate ? 'text-amber-900' : 'text-emerald-900'} text-lg"></i>
                    </div>
                    <div>
                        <h3 class="font-bold text-lg text-gray-800 truncate max-w-[200px]" title="${warp.name}">
                            ${warp.name}
                        </h3>
                        <div class="text-xs text-gray-500">傳送錨點</div>
                    </div>
                </div>
                <span class="text-xs px-3 py-1.5 rounded-full ${visibilityConfig.color} border font-semibold shadow-sm">
                    ${visibilityConfig.badge}
                </span>
            </div>

            <!-- 資訊區域 -->
            <div class="space-y-3">
                <!-- 世界資訊 -->
                <div class="flex items-center p-2 bg-white/50 rounded-lg">
                    <div class="w-8 h-8 bg-gradient-to-br from-blue-400 to-indigo-500 rounded-lg flex items-center justify-center mr-3">
                        <i class="fas fa-globe-asia text-white text-sm"></i>
                    </div>
                    <div class="flex-1">
                        <div class="text-xs text-gray-500 font-medium">世界</div>
                        <div class="text-sm font-semibold text-gray-800">${worldDisplay}</div>
                    </div>
                </div>

                <!-- 座標資訊 -->
                <div class="flex items-center p-2 bg-white/50 rounded-lg">
                    <div class="w-8 h-8 bg-gradient-to-br from-red-400 to-pink-500 rounded-lg flex items-center justify-center mr-3">
                        <i class="fas fa-crosshairs text-white text-sm"></i>
                    </div>
                    <div class="flex-1">
                        <div class="text-xs text-gray-500 font-medium">座標位置</div>
                        <div class="text-sm font-mono font-semibold text-gray-800">
                            X: ${warp.x} • Y: ${warp.y} • Z: ${warp.z}
                        </div>
                    </div>
                </div>

                <!-- 建立者資訊 -->
                <div class="flex items-center p-2 bg-white/50 rounded-lg">
                    <div class="w-8 h-8 bg-purple-500 rounded-lg flex items-center justify-center mr-3">
                        <i class="fas fa-user text-white text-sm"></i>
                    </div>
                    <div class="flex-1">
                        <div class="text-xs text-gray-500 font-medium">建立者</div>
                        <div class="text-sm font-semibold text-gray-800">${warp.creator}</div>
                    </div>
                </div>

                <!-- 時間資訊 -->
                <div class="flex items-center p-2 bg-white/50 rounded-lg">
                    <div class="w-8 h-8 bg-green-500 rounded-lg flex items-center justify-center mr-3">
                        <i class="fas fa-calendar-alt text-white text-sm"></i>
                    </div>
                    <div class="flex-1">
                        <div class="text-xs text-gray-500 font-medium">建立時間</div>
                        <div class="text-sm font-semibold text-gray-800">${warp.createdAt}</div>
                    </div>
                </div>

                ${invitedPlayersHtml}
            </div>

            <!-- 底部裝飾線 -->
            <div class="mt-4 h-1 bg-gradient-to-r ${isPrivate ? 'from-amber-300 to-orange-400' : 'from-emerald-300 to-teal-400'} rounded-full"></div>
        `;

        return card;
    }

    // 應用過濾器
// 應用排序
// 更新過濾統計
    updateFilterStats(filteredCount, totalCount) {
        const filterStats = document.getElementById('filter-stats');
        const filteredCountEl = document.getElementById('filtered-count');
        const totalCountEl = document.getElementById('total-count');

        if (filteredCountEl) filteredCountEl.textContent = filteredCount;
        if (totalCountEl) totalCountEl.textContent = totalCount;

        if (this.hasActiveFilters() && filterStats) {
            filterStats.classList.remove('hidden');
        } else if (filterStats) {
            filterStats.classList.add('hidden');
        }
    }
    connectWebSocket() {
        try {
            this.socket = new WebSocket(this.wsUrl);

            this.socket.onopen = () => {
                console.log('WebSocket 連線成功');
                this.updateConnectionStatus(true);

                setInterval(() => {
                    if (this.socket.readyState === WebSocket.OPEN) {
                        this.socket.send('ping');
                    }
                }, 30000);
            };

            this.socket.onmessage = (event) => {
                try {
                    // 忽略 ping/pong 訊息
                    if (event.data === 'pong' || event.data === 'ping') {
                        return;
                    }

                    const data = JSON.parse(event.data);
                    
                    // 處理配置重載消息
                    if (data.type === 'config_reload') {
                        this.handleConfigReload(data);
                        return;
                    }
                    
                    if (data.type && data.warp) {
                        this.handleWarpUpdate(data);
                    }
                } catch (error) {
                    console.error('處理 WebSocket 訊息失敗:', error);
                }
            };

            this.socket.onclose = () => {
                console.log('WebSocket 連線關閉');
                this.updateConnectionStatus(false);

                setTimeout(() => {
                    this.connectWebSocket();
                }, 5000);
            };

            this.socket.onerror = (error) => {
                console.error('WebSocket 錯誤:', error);
                this.updateConnectionStatus(false);
            };

        } catch (error) {
            console.error('WebSocket 連線失敗:', error);
            this.updateConnectionStatus(false);
        }
    }

    updateConnectionStatus(connected) {
        const indicator = document.getElementById('status-indicator');
        const text = document.getElementById('status-text');

        // 只有在元素存在時才更新狀態
        if (indicator && text) {
            if (connected) {
                indicator.className = 'w-3 h-3 bg-green-500 rounded-full mr-2';
                text.textContent = '即時連線中';
            } else {
                indicator.className = 'w-3 h-3 bg-red-500 rounded-full mr-2';
                text.textContent = '連線中斷';
            }
        }
    }

    handleWarpUpdate(data) {
        console.log('收到傳送點更新:', data);

        this.loadStats();
        this.loadWarps();

        this.showNotification(`傳送點 ${data.warp.name} 已${this.getActionText(data.type)}`, data.type);
    }

    async handleConfigReload(data) {
        console.log('收到配置重載消息:', data);
        
        try {
            // 檢查 Web 功能是否被禁用
            if (data.webEnabled === false) {
                this.showNotification('Web 管理介面已被管理員禁用', 'warning');
                this.updateConnectionStatus(false);
                
                // 顯示禁用狀態
                setTimeout(() => {
                    this.showNotification('頁面將在 5 秒後自動關閉', 'info');
                    setTimeout(() => {
                        window.close();
                    }, 5000);
                }, 2000);
                return;
            }
            
            // 檢查端口是否變更
            if (data.oldPort && data.newPort && data.oldPort !== data.newPort) {
                const currentPort = parseInt(window.location.port) || 8080;
                const newPort = data.newPort;
                
                this.showNotification(`檢測到端口變更 (${data.oldPort} → ${newPort})，正在重新連接...`, 'info');
                
                // 關閉當前 WebSocket 連接
                if (this.socket) {
                    this.socket.close();
                }
                
                // 更新 URL 並重新連接
                const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
                this.wsUrl = `${protocol}//${window.location.hostname}:${newPort}/ws`;
                this.apiUrl = `http://${window.location.hostname}:${newPort}/api`;
                
                // 延遲一點時間後重新連接
                setTimeout(() => {
                    this.connectWebSocket();
                }, 1000);
                
                // 提示用戶可能需要手動更新 URL
                setTimeout(() => {
                    this.showNotification(`如果頁面無法正常工作，請手動訪問: http://${window.location.hostname}:${newPort}`, 'info');
                }, 3000);
                
                return;
            }
            
            // 普通配置重載，重新載入數據
            await this.loadStats(true); // 重新獲取 apiUrl 和 wsUrl
            await this.loadWarps();
            await this.loadTeleportStats();
            
            this.showNotification('配置已重新載入，數據已更新', 'success');
            
        } catch (error) {
            console.error('處理配置重載失敗:', error);
            this.showNotification('處理配置重載時發生錯誤', 'error');
        }
    }

    getActionText(type) {
        switch (type) {
            case 'create': return '建立';
            case 'update': return '更新';
            case 'delete': return '刪除';
            default: return '變更';
        }
    }
    setupEventListeners() {
        // 搜尋輸入框
        const searchInput = document.getElementById('search-input');
        if (searchInput && searchInput instanceof HTMLInputElement) {
            searchInput.addEventListener('input', (e) => {
                const target = e.target;
                if (target instanceof HTMLInputElement) {
                    this.searchQuery = target.value;
                    this.currentPage = 0;
                    this.applyFilter();
                }
            });
        }

        // 過濾器事件監聽器
        const visibilityFilter = document.getElementById('visibility-filter');
        if (visibilityFilter && visibilityFilter instanceof HTMLSelectElement) {
            visibilityFilter.addEventListener('change', (e) => {
                const target = e.target;
                if (target instanceof HTMLSelectElement) {
                    this.visibilityFilter = target.value;
                    this.currentPage = 0;
                    this.applyFilter();
                }
            });
        }

        const worldFilter = document.getElementById('world-filter');
        if (worldFilter && worldFilter instanceof HTMLSelectElement) {
            worldFilter.addEventListener('change', (e) => {
                const target = e.target;
                if (target instanceof HTMLSelectElement) {
                    this.worldFilter = target.value;
                    this.currentPage = 0;
                    this.applyFilter();
                }
            });
        }

        // 清除過濾器按鈕
        const clearFiltersBtn = document.getElementById('clear-filters-btn');
        if (clearFiltersBtn) {
            clearFiltersBtn.addEventListener('click', async () => {
                await this.clearAllFilters();
            });
        }

        // 重新整理按鈕
        const refreshBtn = document.getElementById('refresh-btn');
        if (refreshBtn) {
            refreshBtn.addEventListener('click', async () => {
                refreshBtn.innerHTML = '<div class="loading-spinner mr-2"></div>載入中...';
                refreshBtn.disabled = true;

                try {
                    await this.loadStats();
                    await this.loadWarps();
                    await this.loadTeleportStats();
                    this.showNotification('資料已更新', 'success');
                } catch (error) {
                    this.showNotification('更新失敗', 'error');
                } finally {
                    refreshBtn.innerHTML = '<i class="fas fa-sync-alt mr-2"></i>重新整理';
                    refreshBtn.disabled = false;
                }
            });
        }


        // 分頁切換事件監聽器
        document.getElementById('tab-warps').addEventListener('click', () => {
            this.switchTab('warps');
        });

        document.getElementById('tab-stats').addEventListener('click', () => {
            this.switchTab('stats');
        });

        document.getElementById('tab-settings').addEventListener('click', () => {
            this.switchTab('settings');
        });

        // 設定頁面的開關
        const autoRefreshToggle = document.getElementById('auto-refresh');
        const darkThemeToggle = document.getElementById('dark-theme');
        const pageSizeSelect = document.getElementById('page-size');

        if (autoRefreshToggle && autoRefreshToggle instanceof HTMLInputElement) {
            autoRefreshToggle.addEventListener('change', (e) => {
                const target = e.target;
                if (target instanceof HTMLInputElement) {
                    this.settings.autoRefresh = target.checked;
                    this.saveSettings();
                    if (target.checked) {
                        this.startAutoRefresh();
                    } else {
                        this.stopAutoRefresh();
                    }
                    this.showNotification(`自動重新整理已${target.checked ? '開啟' : '關閉'}`, 'info');
                }
            });
        }

        if (pageSizeSelect && pageSizeSelect instanceof HTMLSelectElement) {
            pageSizeSelect.addEventListener('change', (e) => {
                const target = e.target;
                if (target instanceof HTMLSelectElement) {
                    this.pageSize = parseInt(target.value);
                    this.settings.pageSize = this.pageSize;
                    this.currentPage = 0; // 重置到第一頁
                    this.saveSettings();
                    this.applyFilter(); // 重新應用過濾器以更新顯示
                    this.showNotification(`每頁顯示數量已設定為 ${this.pageSize} 項`, 'info');
                }
            });
        }

        if (darkThemeToggle && darkThemeToggle instanceof HTMLInputElement) {
            darkThemeToggle.addEventListener('change', (e) => {
                const target = e.target;
                if (target instanceof HTMLInputElement) {
                    this.settings.darkTheme = target.checked;
                    this.saveSettings();
                    this.applyTheme();
                    this.showNotification(`暗色主題已${target.checked ? '開啟' : '關閉'}`, 'info');
                }
            });
        }

        document.addEventListener('visibilitychange', () => {
            if (!document.hidden) {
                this.loadStats();
                this.loadWarps();
                this.loadTeleportStats(); // 也刷新傳送統計
            }
        });

        // 當頁面即將關閉時停止自動刷新和清理計時器
        window.addEventListener('beforeunload', () => {
            this.stopAutoRefresh();
            this.cleanup();
        });

        const statCards = document.querySelectorAll('#stats > div');
        statCards.forEach((card, index) => {
            card.classList.add('cursor-pointer', 'hover:shadow-md', 'transition-shadow');
            card.title = '點擊以篩選對應類型的傳送點';
            
            // 添加點擊事件
            card.addEventListener('click', () => {
                let filterValue = 'all';
                switch (index) {
                    case 0: filterValue = 'all'; break;
                    case 1: filterValue = 'public'; break;
                    case 2: filterValue = 'private'; break;
                }
                
                // 更新過濾器
                this.visibilityFilter = filterValue;
                this.currentPage = 0;
                
                // 更新UI元素
                const visibilityFilterSelect = document.getElementById('visibility-filter');
                if (visibilityFilterSelect && visibilityFilterSelect instanceof HTMLSelectElement) {
                    visibilityFilterSelect.value = filterValue;
                }
                
                // 應用過濾器
                this.applyFilter();
                
                // 顯示通知
                const filterNames = { 'all': '全部', 'public': '公共', 'private': '私人' };
                this.showNotification(`已切換到${filterNames[filterValue]}傳送點`, 'info');
            });
        });

        // 最近傳送記錄摺疊功能（如果元素存在）
        const toggleButton = document.getElementById('toggle-recent-teleports');
        if (toggleButton) {
            toggleButton.addEventListener('click', () => {
                this.toggleRecentTeleports();
            });
        }

        // 統計刷新按鈕
        const refreshStatsBtn = document.getElementById('refresh-stats-btn');
        if (refreshStatsBtn) {
            refreshStatsBtn.addEventListener('click', async () => {
                refreshStatsBtn.innerHTML = '<div class="loading-spinner mr-2"></div>載入中...';
                refreshStatsBtn.disabled = true;

                try {
                    await this.loadEnhancedStats();
                    this.showNotification('統計已更新', 'success');
                } catch (error) {
                    this.showNotification('更新失敗', 'error');
                } finally {
                    refreshStatsBtn.innerHTML = '<i class="fas fa-sync-alt mr-1"></i>刷新';
                    refreshStatsBtn.disabled = false;
                }
            });
        }
    }

    toggleRecentTeleports() {
        const container = document.getElementById('recent-teleports-container');
        const toggleText = document.getElementById('toggle-text');
        const toggleIcon = document.getElementById('toggle-icon');

        // 只有在所有元素都存在時才執行
        if (container && toggleText && toggleIcon) {
            if (container.style.display === 'none') {
                // 展開
                container.style.display = 'block';
                toggleText.textContent = '隱藏';
                toggleIcon.classList.remove('fa-chevron-down');
                toggleIcon.classList.add('fa-chevron-up');
            } else {
                // 摺疊
                container.style.display = 'none';
                toggleText.textContent = '顯示';
                toggleIcon.classList.remove('fa-chevron-up');
                toggleIcon.classList.add('fa-chevron-down');
            }
        }
    }
// 清除所有過濾器和搜尋
// 分頁切換功能
    switchTab(tabName) {
        // 更新當前分頁
        this.currentTab = tabName;

        // 更新分頁按鈕樣式
        document.querySelectorAll('.tab-button').forEach(btn => {
            btn.classList.remove('active');
        });

        const activeTab = document.getElementById(`tab-${tabName}`);
        activeTab.classList.add('active');

        // 切換分頁內容
        document.querySelectorAll('.tab-content').forEach(content => {
            content.classList.add('hidden');
        });

        document.getElementById(`content-${tabName}`).classList.remove('hidden');

        // 重新應用主題以確保顏色正確
        this.applyTheme();

        // 如果有週間圖表，重新渲染以應用新主題
        if (this.weeklyChart && this.enhancedStats && this.enhancedStats.weeklyStats) {
            this.updateWeeklyStats(this.enhancedStats.weeklyStats);
        }

        // 如果切換到統計分頁，載入統計資料
        if (tabName === 'stats') {
            this.loadEnhancedStats();
            // 延遲一點時間確保DOM已渲染完成，但不傳入空數據
            setTimeout(() => {
                // 只有在有統計數據時才更新圖表
                if (this.enhancedStats) {
                    this.updateCharts(this.enhancedStats);
                }
            }, 100);
        }

        // 如果切換到設定分頁，初始化設定介面
        if (tabName === 'settings') {
            this.initializeSettingsUI();
        }
    }

    // 載入設定
    loadSettings() {
        const defaultSettings = {
            pageSize: 15,
            refreshInterval: 30000,
            autoRefresh: false,
            showCoordinates: true,
            darkTheme: false,
            enableCache: true
        };

        try {
            const saved = localStorage.getItem('signwarpx-settings');
            return saved ? { ...defaultSettings, ...JSON.parse(saved) } : defaultSettings;
        } catch (error) {
            console.error('載入設定失敗:', error);
            return defaultSettings;
        }
    }

    // 儲存設定
    saveSettings() {
        try {
            localStorage.setItem('signwarpx-settings', JSON.stringify(this.settings));
            this.showNotification('設定已儲存', 'success');
        } catch (error) {
            console.error('儲存設定失敗:', error);
            this.showNotification('儲存設定失敗', 'error');
        }
    }

    // 初始化設定介面
    initializeSettingsUI() {
        // 設定開關狀態
        const autoRefreshToggle = document.getElementById('auto-refresh');
        const darkThemeToggle = document.getElementById('dark-theme');
        const pageSizeSelect = document.getElementById('page-size');

        if (autoRefreshToggle) autoRefreshToggle.checked = this.settings.autoRefresh;
        if (darkThemeToggle) darkThemeToggle.checked = this.settings.darkTheme;
        if (pageSizeSelect) pageSizeSelect.value = this.settings.pageSize.toString();
    }


    // 匯出統計報告
// 應用主題
    applyTheme() {
        const body = document.body;

        // 移除現有的主題樣式
        const existingDarkStyle = document.getElementById('dark-theme-styles');
        const existingLightStyle = document.getElementById('light-theme-styles');
        if (existingDarkStyle) {
            existingDarkStyle.remove();
        }
        if (existingLightStyle) {
            existingLightStyle.remove();
        }

        if (this.settings.darkTheme) {
            // 暗色主題 - 深色背景確保文字可讀性
            body.style.background = 'linear-gradient(135deg, #1e293b 0%, #334155 100%)';
            body.style.color = '#f1f5f9';
            body.classList.add('dark-theme');

            // 添加暗色主題樣式
            const style = document.createElement('style');
            style.id = 'dark-theme-styles';
            style.textContent = `
                .dark-theme .glass-effect {
                    background: rgba(30, 41, 59, 0.8) !important;
                    border: 1px solid rgba(51, 65, 85, 0.3) !important;
                    color: #f1f5f9 !important;
                }
                .dark-theme .hero-section {
                    background: rgba(30, 41, 59, 0.9) !important;
                    color: #f1f5f9 !important;
                }
                .dark-theme .stats-card {
                    background: rgba(30, 41, 59, 0.7) !important;
                    color: #f1f5f9 !important;
                }
                .dark-theme .warp-card {
                    background: rgba(30, 41, 59, 0.95) !important;
                    color: #f1f5f9 !important;
                }
                .dark-theme .tab-button:not(.active) {
                    color: #cbd5e1 !important;
                }
                .dark-theme .tab-button.active {
                    color: #ffffff !important;
                }
                .dark-theme .hero-section h1 {
                    color: #f1f5f9 !important;
                }
                .dark-theme .hero-section p {
                    color: #cbd5e1 !important;
                }
                .dark-theme .hero-section i {
                    color: #60a5fa !important;
                }
                .dark-theme .stats-card i {
                    opacity: 0.9 !important;
                }
                .dark-theme .stats-card .text-gray-600 {
                    color: #cbd5e1 !important;
                }
                .dark-theme .stats-card .text-gray-800 {
                    color: #f1f5f9 !important;
                }
                .dark-theme h1, .dark-theme h2, .dark-theme h3, .dark-theme h4, .dark-theme h5, .dark-theme h6 {
                    color: #f1f5f9 !important;
                }
                .dark-theme p, .dark-theme span, .dark-theme div {
                    color: #cbd5e1 !important;
                }
                .dark-theme .text-gray-800 {
                    color: #f1f5f9 !important;
                }
                .dark-theme .text-gray-700 {
                    color: #e2e8f0 !important;
                }
                .dark-theme .text-gray-600 {
                    color: #cbd5e1 !important;
                }
                .dark-theme .text-gray-500 {
                    color: #94a3b8 !important;
                }
                .dark-theme .text-gray-400 {
                    color: #64748b !important;
                }
                .dark-theme #empty-state h3 {
                    color: #f1f5f9 !important;
                }
                .dark-theme #empty-state p {
                    color: #cbd5e1 !important;
                }
                .dark-theme #empty-state i {
                    color: #64748b !important;
                }
                .dark-theme #loading p {
                    color: #cbd5e1 !important;
                }
                .dark-theme .input-field {
                    background: rgba(51, 65, 85, 0.8) !important;
                    color: #f1f5f9 !important;
                    border-color: rgba(71, 85, 105, 0.5) !important;
                }
                .dark-theme .input-field::placeholder {
                    color: #94a3b8 !important;
                }
                .dark-theme .input-field:focus {
                    background: rgba(51, 65, 85, 1) !important;
                    border-color: #60a5fa !important;
                }
                .dark-theme .toggle-slider {
                    background: linear-gradient(135deg, #475569 0%, #64748b 100%) !important;
                }
                .dark-theme input:checked + .toggle-slider {
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
                }
                .dark-theme .btn-primary {
                    color: #ffffff !important;
                }
                .dark-theme .btn-secondary {
                    color: #ffffff !important;
                }
                .dark-theme .btn-success {
                    color: #ffffff !important;
                }
                .dark-theme .notification {
                    background: rgba(30, 41, 59, 0.95) !important;
                    color: #f1f5f9 !important;
                    border: 1px solid rgba(51, 65, 85, 0.3) !important;
                }
                .dark-theme #content-stats h2 {
                    color: #f1f5f9 !important;
                }
                .dark-theme #content-stats h3 {
                    color: #f1f5f9 !important;
                }
                .dark-theme #content-stats .font-semibold {
                    color: #f1f5f9 !important;
                }
                .dark-theme #content-settings h2 {
                    color: #f1f5f9 !important;
                }
                .dark-theme #content-settings h3 {
                    color: #f1f5f9 !important;
                }
                .dark-theme #content-settings p {
                    color: #cbd5e1 !important;
                }
                .dark-theme #content-warps h2 {
                    color: #f1f5f9 !important;
                }
                .dark-theme .loading-spinner {
                    border-top-color: #60a5fa !important;
                }
                .dark-theme .warp-card h3 {
                    color: #f1f5f9 !important;
                }
                .dark-theme .warp-card .text-xs {
                    color: #94a3b8 !important;
                }
                .dark-theme .warp-card .text-sm {
                    color: #cbd5e1 !important;
                }
                .dark-theme .warp-card .font-semibold {
                    color: #f1f5f9 !important;
                }
                .dark-theme .text-purple-800 {
                    color: #e2e8f0 !important;
                }
                .dark-theme .from-purple-100 {
                    background: linear-gradient(to right, rgba(139, 92, 246, 0.3), rgba(99, 102, 241, 0.3)) !important;
                }
                .dark-theme .border-purple-200 {
                    border-color: rgba(139, 92, 246, 0.5) !important;
                }
                .dark-theme .bg-white\\/50 {
                    background: rgba(71, 85, 105, 0.3) !important;
                }
                .dark-theme .border-gray-200 {
                    border-color: rgba(71, 85, 105, 0.5) !important;
                }
                .dark-theme .text-gray-700 {
                    color: #e2e8f0 !important;
                }
                .dark-theme .text-gray-600 {
                    color: #cbd5e1 !important;
                }
                .dark-theme .warp-card .font-mono {
                    color: #e2e8f0 !important;
                }
                .dark-theme .status-badge {
                    color: #ffffff !important;
                }
                .dark-theme .createPageButton {
                    background: rgba(51, 65, 85, 0.8) !important;
                    color: #cbd5e1 !important;
                    border-color: rgba(71, 85, 105, 0.5) !important;
                }
                .dark-theme .createPageButton:hover {
                    background: rgba(71, 85, 105, 0.8) !important;
                    color: #f1f5f9 !important;
                }
                .dark-theme label {
                    color: #f1f5f9 !important;
                }
                .dark-theme .text-center {
                    color: #cbd5e1 !important;
                }
                .dark-theme .font-bold {
                    color: #f1f5f9 !important;
                }
                .dark-theme .font-medium {
                    color: #e2e8f0 !important;
                }
                .dark-theme .opacity-75 {
                    color: #94a3b8 !important;
                }
                .dark-theme .opacity-90 {
                    color: #cbd5e1 !important;
                }
                .dark-theme table {
                    background: rgba(30, 41, 59, 0.8) !important;
                    color: #f1f5f9 !important;
                }
                .dark-theme th {
                    color: #f1f5f9 !important;
                    background: rgba(51, 65, 85, 0.8) !important;
                }
                .dark-theme td {
                    color: #cbd5e1 !important;
                    border-color: rgba(71, 85, 105, 0.3) !important;
                }
                .dark-theme tr:hover {
                    background: rgba(51, 65, 85, 0.3) !important;
                }
                .dark-theme .modal-content {
                    background: rgba(30, 41, 59, 0.95) !important;
                    color: #f1f5f9 !important;
                }
                .dark-theme .warp-card .text-purple-700 {
                    color: #c4b5fd !important;
                }
                .dark-theme .warp-card .text-purple-800 {
                    color: #e9d5ff !important;
                }
                .dark-theme .warp-card .bg-white\/60 {
                    background: rgba(51, 65, 85, 0.6) !important;
                }
                .dark-theme .warp-card .border-purple-200 {
                    border-color: rgba(139, 92, 246, 0.3) !important;
                }
                .dark-theme .warp-card .from-purple-100 {
                    background: linear-gradient(135deg, rgba(139, 92, 246, 0.2) 0%, rgba(124, 58, 237, 0.2) 100%) !important;
                }
                .dark-theme .warp-card .to-indigo-100 {
                    background: linear-gradient(135deg, rgba(139, 92, 246, 0.2) 0%, rgba(124, 58, 237, 0.2) 100%) !important;
                }
                .dark-theme #content-warps .glass-effect h2 {
                    color: #f1f5f9 !important;
                }
                .dark-theme #content-stats .glass-effect h2 {
                    color: #f1f5f9 !important;
                }
                .dark-theme #content-settings .glass-effect h2 {
                    color: #f1f5f9 !important;
                }
                .dark-theme .glass-effect h2 {
                    color: #f1f5f9 !important;
                }
                .dark-theme .glass-effect h3 {
                    color: #f1f5f9 !important;
                }
                .dark-theme .glass-effect .text-2xl {
                    color: #f1f5f9 !important;
                }
                .dark-theme .glass-effect .font-bold {
                    color: #f1f5f9 !important;
                }
                .dark-theme .glass-effect .text-gray-800 {
                    color: #f1f5f9 !important;
                }
                .dark-theme .glass-effect .text-gray-700 {
                    color: #e2e8f0 !important;
                }
                .dark-theme .glass-effect .text-gray-600 {
                    color: #cbd5e1 !important;
                }
                .dark-theme .warp-card .fa-user-friends {
                    color: #c4b5fd !important;
                }
                .dark-theme .warp-card span.inline-flex {
                    background: linear-gradient(135deg, rgba(139, 92, 246, 0.3) 0%, rgba(124, 58, 237, 0.3) 100%) !important;
                    color: #e9d5ff !important;
                    border-color: rgba(139, 92, 246, 0.4) !important;
                }
                .dark-theme .warp-card .text-purple-800 {
                    color: #e9d5ff !important;
                }
                .dark-theme #pagination button {
                    background: rgba(51, 65, 85, 0.8) !important;
                    color: #cbd5e1 !important;
                    border-color: rgba(71, 85, 105, 0.5) !important;
                }
                .dark-theme #pagination button:hover {
                    background: rgba(71, 85, 105, 0.8) !important;
                    color: #f1f5f9 !important;
                    border-color: rgba(99, 102, 241, 0.5) !important;
                }
                .dark-theme #pagination button.bg-blue-500 {
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
                    color: #ffffff !important;
                    border-color: rgba(102, 126, 234, 0.6) !important;
                }
                .dark-theme #pagination button.bg-blue-500:hover {
                    background: linear-gradient(135deg, #5a67d8 0%, #6b46c1 100%) !important;
                    box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4) !important;
                }
                .dark-theme #page-info {
                    color: #cbd5e1 !important;
                }
                .dark-theme #hourly-stats .bg-gray-200 {
                    background: rgba(71, 85, 105, 0.5) !important;
                }
                .dark-theme #hourly-stats .bg-indigo-500 {
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
                }
                .dark-theme #weekly-stats .bg-gray-200 {
                    background: rgba(71, 85, 105, 0.5) !important;
                }
                .dark-theme #weekly-stats .bg-teal-500 {
                    background: linear-gradient(135deg, #14b8a6 0%, #0891b2 100%) !important;
                }
                .dark-theme #hourly-stats .text-gray-600 {
                    color: #cbd5e1 !important;
                }
                .dark-theme #hourly-stats .text-gray-800 {
                    color: #f1f5f9 !important;
                }
                
                /* 排行榜文字顏色 - 深色模式 */
                .dark-theme .warp-name-text {
                    color: #e2e8f0 !important;
                }
                
                .dark-theme .warp-desc-text {
                    color: #94a3b8 !important;
                }
                
                .dark-theme .warp-count-text {
                    color: #f1f5f9 !important;
                }
                
                .dark-theme .player-name-text {
                    color: #e2e8f0 !important;
                }
                
                .dark-theme .player-online-text {
                    color: #475569 !important;
                    font-weight: 600 !important;
                }
                
                .dark-theme .player-offline-text {
                    color: #475569 !important;
                    font-weight: 600 !important;
                }
                
                .dark-theme .player-count-text {
                    color: #f1f5f9 !important;
                }
                
                .dark-theme .player-desc-text {
                    color: #475569 !important;
                    font-weight: 600 !important;
                }
                
                /* 排行榜背景顏色 - 深色模式 */
                .dark-theme #popular-warps .bg-white {
                    background: rgba(51, 65, 85, 0.6) !important;
                    border-color: rgba(71, 85, 105, 0.5) !important;
                }
                
                .dark-theme #popular-warps .hover\\:bg-gray-50:hover {
                    background: rgba(71, 85, 105, 0.4) !important;
                }
                
                .dark-theme #active-users-list .bg-white {
                    background: rgba(30, 41, 59, 0.8) !important;
                    border-color: rgba(51, 65, 85, 0.7) !important;
                }
                
                .dark-theme #active-users-list .hover\\:bg-gray-50:hover {
                    background: rgba(51, 65, 85, 0.6) !important;
                }
                
                /* 活躍玩家排行榜圖標顏色 - 深色模式 */
                .dark-theme #active-users-list .text-gray-600 {
                    color: #94a3b8 !important;
                }
                
                /* 確保玩家名稱和數字在深色模式下可見 */
                .dark-theme #active-users-list .player-name-text {
                    color: #ffffff !important;
                    font-weight: 700 !important;
                    text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5) !important;
                }
                
                .dark-theme #active-users-list .player-count-text {
                    color: #ffffff !important;
                    font-weight: 800 !important;
                    text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5) !important;
                }
                
                .dark-theme #weekly-stats .text-gray-600 {
                    color: #cbd5e1 !important;
                }
                .dark-theme #weekly-stats .text-gray-800 {
                    color: #f1f5f9 !important;
                }
                .dark-theme #dimension-legend .text-gray-700 {
                    color: #e2e8f0 !important;
                }
                .dark-theme #dimension-legend .text-gray-800 {
                    color: #f1f5f9 !important;
                }
                .dark-theme #dimension-legend .text-gray-500 {
                    color: #94a3b8 !important;
                }
                .dark-theme #dimension-legend .hover\\:bg-gray-50:hover {
                    background: rgba(71, 85, 105, 0.3) !important;
                }
                .dark-theme #cross-dimension-stats .text-gray-600 {
                    color: #cbd5e1 !important;
                }
                .dark-theme #cross-dimension-stats .font-semibold {
                    color: #f1f5f9 !important;
                }
                .dark-theme #cross-dimension-stats .text-gray-500 {
                    color: #94a3b8 !important;
                }
                .dark-theme #popular-routes {
                    color: #cbd5e1 !important;
                }
                .dark-theme .stats-card .text-blue-600 {
                    color: #60a5fa !important;
                }
                .dark-theme .stats-card .text-green-600 {
                    color: #34d399 !important;
                }
                .dark-theme .stats-card .text-purple-600 {
                    color: #a78bfa !important;
                }
                .dark-theme .stats-card .text-red-600 {
                    color: #f87171 !important;
                }
                .dark-theme .stats-card .text-orange-600 {
                    color: #fb923c !important;
                }
                .dark-theme .stats-card .text-green-600 {
                    color: #22c55e !important;
                }
                .dark-theme .stats-card .text-blue-600 {
                    color: #3b82f6 !important;
                }
                .dark-theme #enhanced-total-teleports,
                .dark-theme #enhanced-today-teleports,
                .dark-theme #enhanced-unique-players,
                .dark-theme #most-popular-warp-count {
                    color: #f1f5f9 !important;
                }
                .dark-theme #total-teleports-trend,
                .dark-theme #today-avg-comparison,
                .dark-theme #players-activity-rate,
                .dark-theme #most-popular-warp-name {
                    color: #cbd5e1 !important;
                }
                .dark-theme #avg-daily-teleports,
                .dark-theme #week-active-players,
                .dark-theme #month-active-players {
                    color: #f1f5f9 !important;
                }
                .dark-theme .warp-card .text-yellow-500 {
                    color: #eab308 !important;
                }
                .dark-theme .warp-card .text-purple-500 {
                    color: #a855f7 !important;
                }
                .dark-theme .warp-card .text-indigo-500 {
                    color: #6366f1 !important;
                }
                .dark-theme .warp-card .text-teal-500 {
                    color: #14b8a6 !important;
                }
                .dark-theme .warp-card .text-orange-500 {
                    color: #f97316 !important;
                }
                .dark-theme .warp-card .text-gray-500 {
                    color: #94a3b8 !important;
                }
                .dark-theme .bg-gray-50 {
                    background: rgba(51, 65, 85, 0.8) !important;
                }
                .dark-theme thead th {
                    background: rgba(51, 65, 85, 0.8) !important;
                    color: #f1f5f9 !important;
                }
                .dark-theme tbody tr {
                    background: rgba(30, 41, 59, 0.8) !important;
                }
                .dark-theme tbody tr:nth-child(even) {
                    background: rgba(51, 65, 85, 0.4) !important;
                }
                .dark-theme tbody tr:hover {
                    background: rgba(51, 65, 85, 0.6) !important;
                }
                .dark-theme tbody td {
                    color: #cbd5e1 !important;
                    border-color: rgba(71, 85, 105, 0.3) !important;
                }
                .dark-theme .bg-blue-100 {
                    background: rgba(59, 130, 246, 0.3) !important;
                }
                .dark-theme .text-blue-800 {
                    color: #93c5fd !important;
                }
                .dark-theme .bg-green-400 {
                    background: #22c55e !important;
                }
                .dark-theme .bg-red-400 {
                    background: #ef4444 !important;
                }
                .dark-theme .divide-gray-200 > :not([hidden]) ~ :not([hidden]) {
                    border-color: rgba(71, 85, 105, 0.3) !important;
                }
                .dark-theme .text-xs.text-gray-500 {
                    color: #94a3b8 !important;
                }
                .dark-theme .text-sm.text-gray-600 {
                    color: #cbd5e1 !important;
                }
                .dark-theme .font-medium.text-gray-500 {
                    color: #94a3b8 !important;
                }
                .dark-theme .uppercase.tracking-wider {
                    color: #f1f5f9 !important;
                }
                .dark-theme #active-users-list .text-gray-800 {
                    color: #f1f5f9 !important;
                }
                .dark-theme #active-users-list .text-green-600 {
                    color: #22c55e !important;
                }
                .dark-theme #active-users-list .text-red-600 {
                    color: #ef4444 !important;
                }
                .dark-theme #active-users-list .text-lg {
                    color: #f1f5f9 !important;
                }
                .dark-theme #active-users-list .font-bold {
                    color: #f1f5f9 !important;
                }
                .dark-theme #active-users-list .text-gray-500 {
                    color: #94a3b8 !important;
                }
                .dark-theme #popular-warps .text-gray-800 {
                    color: #f1f5f9 !important;
                }
                .dark-theme #popular-warps .text-lg {
                    color: #f1f5f9 !important;
                }
                .dark-theme #popular-warps .font-bold {
                    color: #f1f5f9 !important;
                }
                .dark-theme #popular-warps .text-gray-500 {
                    color: #94a3b8 !important;
                }
                .dark-theme #recent-teleports tr {
                    background: rgba(15, 23, 42, 0.9) !important;
                }
                .dark-theme #recent-teleports .text-gray-900 {
                    color: #f1f5f9 !important;
                }
                .dark-theme #recent-teleports .text-gray-500 {
                    color: #cbd5e1 !important;
                }
                .dark-theme #recent-teleports .font-medium {
                    color: #f1f5f9 !important;
                }
                .dark-theme .warp-name-text {
                    color: #f1f5f9 !important;
                }
                .dark-theme .warp-desc-text {
                    color: #94a3b8 !important;
                }
                .dark-theme .warp-count-text {
                    color: #f1f5f9 !important;
                }
                .dark-theme .player-name-text {
                    color: #f1f5f9 !important;
                }
                .dark-theme .player-desc-text {
                    color: #94a3b8 !important;
                }
                .dark-theme .player-count-text {
                    color: #f1f5f9 !important;
                }
                .dark-theme .player-online-text {
                    color: #22c55e !important;
                }
                .dark-theme .player-offline-text {
                    color: #ef4444 !important;
                }
                
                /* 修復邀請玩家列表在深色模式下的文字可見性 */
                .dark-theme #invited-players-list .text-gray-800 {
                    color: #f1f5f9 !important;
                }
                .dark-theme #invited-players-list .font-medium {
                    color: #f1f5f9 !important;
                }
                .dark-theme #invited-players-list .text-green-800 {
                    color: #22c55e !important;
                }
                .dark-theme #invited-players-list .text-red-800 {
                    color: #ef4444 !important;
                }
                .dark-theme #invited-players-list .bg-green-100 {
                    background: rgba(34, 197, 94, 0.2) !important;
                }
                .dark-theme #invited-players-list .bg-red-100 {
                    background: rgba(239, 68, 68, 0.2) !important;
                }
                .dark-theme #invited-players-list .bg-white {
                    background: rgba(30, 41, 59, 0.8) !important;
                }
                .dark-theme #invited-players-list .border {
                    border-color: rgba(71, 85, 105, 0.3) !important;
                }
                .dark-theme #invited-players-list .text-gray-500 {
                    color: #94a3b8 !important;
                }
            `;
            document.head.appendChild(style);
        } else {
            // 亮色主題 - 淺色背景確保文字可讀性
            body.style.background = 'linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%)';
            body.style.color = '#1e293b';
            body.classList.remove('dark-theme');

            // 添加亮色主題的自定義類別樣式
            const lightStyle = document.createElement('style');
            lightStyle.id = 'light-theme-styles';
            lightStyle.textContent = `
                .warp-name-text {
                    color: #1f2937 !important;
                }
                .warp-desc-text {
                    color: #6b7280 !important;
                }
                .warp-count-text {
                    color: #1f2937 !important;
                }
                .player-name-text {
                    color: #1f2937 !important;
                }
                .player-desc-text {
                    color: #6b7280 !important;
                }
                .player-count-text {
                    color: #1f2937 !important;
                }
                .player-online-text {
                    color: #059669 !important;
                }
                .player-offline-text {
                    color: #dc2626 !important;
                }
            `;
            document.head.appendChild(lightStyle);
        }
    }

    // 設置分頁事件監聽器
// 設置過濾器事件監聽器
// 設置設定頁面事件監聽器
// 禁用/啟用所有分頁按鈕
    disableTabButtons(disable) {
        const tabButtons = document.querySelectorAll('.tab-button');
        tabButtons.forEach(btn => {
            btn.disabled = disable;
            if (disable) {
                btn.classList.add('opacity-50', 'cursor-not-allowed');
            } else {
                btn.classList.remove('opacity-50', 'cursor-not-allowed');
            }
        });
    }
    // 載入私人傳送錨點選項
// 載入在線玩家列表
    async loadOnlinePlayersForInvite(selectedWarp) {
        const playerSelect = document.getElementById('player-select');
        const playerInput = document.getElementById('player-input');
        if (!playerSelect || !playerInput) return;

        try {
            // 獲取在線玩家列表
            const response = await fetch(`${this.apiUrl}/players/online`);
            const data = await response.json();
            const onlinePlayers = data.players || [];

            playerSelect.innerHTML = '<option value="">選擇要邀請的玩家...</option>';
            playerSelect.disabled = false;
            
            // 啟用手動輸入框
            playerInput.disabled = false;
            playerInput.value = '';

            if (onlinePlayers.length === 0) {
                playerSelect.innerHTML = '<option value="">目前沒有在線玩家</option>';
                playerSelect.disabled = true;
            } else {
                // 過濾掉已經被邀請的玩家和傳送點建立者
                const invitedPlayers = selectedWarp.invitedPlayers || [];
                const availablePlayers = onlinePlayers.filter(player => 
                    player !== selectedWarp.creator && !invitedPlayers.includes(player)
                );

                if (availablePlayers.length === 0) {
                    playerSelect.innerHTML = '<option value="">所有在線玩家都已被邀請或為建立者</option>';
                    // 不要禁用下拉選單，讓用戶可以看到提示
                    playerSelect.disabled = false;
                } else {
                    availablePlayers.forEach(player => {
                        const option = document.createElement('option');
                        option.value = String(player);
                        option.textContent = `${player} (在線)`;
                        playerSelect.appendChild(option);
                    });
                }

                // 總是顯示所有在線玩家作為參考（包括已邀請的）
                if (onlinePlayers.length > 0) {
                    const separator = document.createElement('option');
                    separator.disabled = true;
                    separator.textContent = '--- 所有在線玩家 (參考) ---';
                    playerSelect.appendChild(separator);

                    onlinePlayers.forEach(player => {
                        const option = document.createElement('option');
                        option.disabled = true; // 禁用但顯示
                        option.value = '';
                        let status;
                        if (player === selectedWarp.creator) {
                            status = ' (建立者)';
                        } else if (invitedPlayers.includes(player)) {
                            status = ' (已邀請)';
                        } else {
                            status = ' (可邀請)';
                        }
                        option.textContent = `${player}${status}`;
                        playerSelect.appendChild(option);
                    });
                }
            }
        } catch (error) {
            console.error('載入在線玩家失敗:', error);
            this.showNotification('載入玩家列表失敗', 'error');
            playerSelect.innerHTML = '<option value="">載入失敗</option>';
            playerSelect.disabled = true;
            playerInput.disabled = false; // 即使載入失敗，仍然允許手動輸入
        }
    }

    // 更新已邀請玩家列表顯示
    updateInvitedPlayersList(selectedWarp) {
        const invitedPlayersList = document.getElementById('invited-players-list');
        if (!invitedPlayersList) return;

        const invitedPlayers = selectedWarp.invitedPlayers || [];
        
        if (invitedPlayers.length === 0) {
            invitedPlayersList.innerHTML = `
                <p class="text-gray-500 text-sm text-center">
                    <i class="fas fa-users mr-2"></i>
                    尚未邀請任何玩家
                </p>
            `;
            return;
        }

        invitedPlayersList.innerHTML = invitedPlayers.map(player => `
            <div class="flex items-center justify-between bg-white rounded-lg p-2 mb-2 border">
                <div class="flex items-center">
                    <div class="w-8 h-8 bg-blue-500 rounded-full flex items-center justify-center mr-3">
                        <i class="fas fa-user text-white text-sm"></i>
                    </div>
                    <span class="font-medium text-gray-800">${player}</span>
                    <span class="ml-2 text-xs px-2 py-1 bg-green-100 text-green-800 rounded-full">
                        ${this.onlineStatus[player] ? '在線' : '離線'}
                    </span>
                </div>
                <button class="remove-invite-btn text-red-500 hover:text-red-700 p-1" 
                        data-player="${player}" data-warp="${selectedWarp.name}">
                    <i class="fas fa-times"></i>
                </button>
            </div>
        `).join('');

        // 為移除按鈕添加事件監聽器
        invitedPlayersList.querySelectorAll('.remove-invite-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const player = e.currentTarget.dataset.player;
                const warpName = e.currentTarget.dataset.warp;
                this.removePlayerInvite(warpName, player);
            });
        });
    }

    // 設置邀請模態框的事件監聽器
// 邀請玩家到傳送錨點
// 移除玩家邀請
    async removePlayerInvite(warpName, playerName) {
        if (!confirm(`確定要移除 ${playerName} 對傳送錨點 ${warpName} 的邀請嗎？`)) {
            return;
        }

        try {
            const response = await fetch(`${this.apiUrl}/warps/${warpName}/uninvite`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ player: playerName })
            });

            const result = await response.json();

            if (response.ok) {
                this.showNotification(`已移除 ${playerName} 的邀請`, 'success');
                
                // 重新載入傳送點數據
                await this.loadWarps();
                
                // 更新模態框中的顯示
                const warpSelect = document.getElementById('warp-select');
                if (warpSelect?.value) {
                    const updatedWarp = this.allWarps.find(w => w.name === warpName);
                    if (updatedWarp) {
                        const selectedOption = warpSelect.selectedOptions[0];
                        if (selectedOption) {
                            selectedOption.dataset.warpData = JSON.stringify(updatedWarp);
                        }
                        await this.loadOnlinePlayersForInvite(updatedWarp);
                        this.updateInvitedPlayersList(updatedWarp);
                    }
                }
            } else {
                this.showNotification(result.message || '移除邀請失敗', 'error');
            }
        } catch (error) {
            console.error('移除邀請失敗:', error);
            this.showNotification('移除邀請操作失敗', 'error');
        }
    }

    // 關閉邀請模態框
// 清理所有計時器和狀態
    cleanup() {
        if (this.paginationDebounceTimer) {
            clearTimeout(this.paginationDebounceTimer);
            this.paginationDebounceTimer = null;
        }
        if (this.tabSwitchDebounceTimer) {
            clearTimeout(this.tabSwitchDebounceTimer);
            this.tabSwitchDebounceTimer = null;
        }
        this.isProcessingPagination = false;
        this.disablePaginationButtons(false);
        this.disableTabButtons(false);
    }

    // 顯示通知
    showNotification(message, type = 'info') {
        const container = document.getElementById('notifications');
        if (!container) return;

        const notification = document.createElement('div');
        notification.className = `notification bg-white border-l-4 p-4 rounded-lg shadow-lg mb-2 transform translate-x-full transition-transform duration-300`;
        
        const colors = {
            success: 'border-green-500',
            error: 'border-red-500',
            warning: 'border-yellow-500',
            info: 'border-blue-500'
        };

        const icons = {
            success: 'fa-check-circle text-green-500',
            error: 'fa-exclamation-circle text-red-500',
            warning: 'fa-exclamation-triangle text-yellow-500',
            info: 'fa-info-circle text-blue-500'
        };

        notification.classList.add(colors[type] || colors.info);
        
        notification.innerHTML = `
            <div class="flex items-center">
                <i class="fas ${icons[type] || icons.info} mr-3"></i>
                <span class="text-gray-800">${message}</span>
                <button class="ml-auto text-gray-400 hover:text-gray-600" onclick="this.parentElement.parentElement.remove()">
                    <i class="fas fa-times"></i>
                </button>
            </div>
        `;

        container.appendChild(notification);

        // 顯示動畫
        setTimeout(() => {
            notification.classList.remove('translate-x-full');
        }, 100);

        // 自動移除
        setTimeout(() => {
            notification.classList.add('translate-x-full');
            setTimeout(() => {
                if (notification.parentElement) {
                    notification.remove();
                }
            }, 300);
        }, 5000);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    new WarpManager();
});