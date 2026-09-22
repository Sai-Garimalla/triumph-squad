import React, { useState, useEffect } from 'react'

export default function App() {
  const [orders, setOrders] = useState([])
  const [inventory, setInventory] = useState([])
  const [metrics, setMetrics] = useState({
    totalOrders: 0,
    confirmedOrders: 0,
    outOfStockOrders: 0,
    dlqOrders: 0,
    processingOrders: 0,
    failedOrders: 0
  })
  const [dlqItems, setDlqItems] = useState([])
  const [isBursting, setIsBursting] = useState(false)
  const [burstCount, setBurstCount] = useState(50)
  const [activeTab, setActiveTab] = useState('ALL')
  const [showDlqModal, setShowDlqModal] = useState(false)
  const [selectedTimeline, setSelectedTimeline] = useState(null)
  const [timelineLoading, setTimelineLoading] = useState(false)

  // Polling data every 800ms for ultra-responsive live dashboard feel
  const fetchData = async () => {
    try {
      const [ordersRes, invRes, metricsRes, dlqRes] = await Promise.all([
        fetch('/api/orders').catch(() => null),
        fetch('/api/inventory').catch(() => null),
        fetch('/api/orders/metrics').catch(() => null),
        fetch('/api/dlq').catch(() => null)
      ])

      if (ordersRes && ordersRes.ok) {
        const data = await ordersRes.json()
        setOrders(data)
      }
      if (invRes && invRes.ok) {
        const data = await invRes.json()
        setInventory(data)
      }
      if (metricsRes && metricsRes.ok) {
        const data = await metricsRes.json()
        setMetrics(data)
      }
      if (dlqRes && dlqRes.ok) {
        const data = await dlqRes.json()
        setDlqItems(data)
      }
    } catch (e) {
      console.error('Fetch error:', e)
    }
  }

  useEffect(() => {
    fetchData()
    const interval = setInterval(fetchData, 800)
    return () => clearInterval(interval)
  }, [])

  const triggerBurst = async () => {
    setIsBursting(true)
    try {
      await fetch(`/api/orders/simulate-burst?totalOrders=${burstCount}&productSku=PROD-PHONE&quantityPerOrder=1`, {
        method: 'POST'
      })
      setTimeout(() => {
        fetchData()
        setIsBursting(false)
      }, 1000)
    } catch (e) {
      console.error(e)
      setIsBursting(false)
    }
  }

  const resetInventory = async () => {
    try {
      await fetch('/api/inventory/reset?stockPerWarehouse=25', { method: 'POST' })
      fetchData()
    } catch (e) {
      console.error(e)
    }
  }

  const reprocessDlq = async (id) => {
    try {
      await fetch(`/api/dlq/${id}/retry`, { method: 'POST' })
      fetchData()
    } catch (e) {
      console.error(e)
    }
  }

  const viewTimeline = async (orderId) => {
    setTimelineLoading(true)
    try {
      const res = await fetch(`/api/orders/${orderId}/timeline`)
      if (res.ok) {
        const data = await res.json()
        setSelectedTimeline({ orderId, logs: data })
      }
    } catch (e) {
      console.error(e)
    } finally {
      setTimelineLoading(false)
    }
  }

  const getStatusBadge = (status) => {
    switch (status) {
      case 'ORDER_CONFIRMED':
      case 'PACKED':
      case 'SHIPPED':
      case 'DELIVERED':
        return <span className="badge badge-confirmed">● {status}</span>
      case 'OUT_OF_STOCK':
        return <span className="badge badge-out_of_stock">✕ OUT OF STOCK</span>
      case 'DLQ':
        return <span className="badge badge-dlq">⚠ DLQ QUARANTINED</span>
      case 'PAYMENT_PROCESSING':
      case 'INVENTORY_RESERVED':
      case 'CREATED':
        return <span className="badge badge-processing">↻ {status}</span>
      case 'PAYMENT_FAILED':
      case 'PROCESSING_FAILED':
        return <span className="badge badge-failed">✕ FAILED</span>
      default:
        return <span className="badge">{status}</span>
    }
  }

  const filteredOrders = orders.filter(o => {
    if (activeTab === 'ALL') return true
    if (activeTab === 'CONFIRMED') return ['ORDER_CONFIRMED', 'PACKED', 'SHIPPED'].includes(o.status)
    if (activeTab === 'OUT_OF_STOCK') return o.status === 'OUT_OF_STOCK'
    if (activeTab === 'DLQ') return o.status === 'DLQ'
    if (activeTab === 'PROCESSING') return ['CREATED', 'INVENTORY_RESERVED', 'PAYMENT_PROCESSING'].includes(o.status)
    return true
  })

  // Calculate total available and total sold across warehouses
  const totalAvailable = inventory.reduce((acc, curr) => acc + curr.availableQuantity, 0)
  const totalReserved = inventory.reduce((acc, curr) => acc + curr.reservedQuantity, 0)
  const totalSold = inventory.reduce((acc, curr) => acc + curr.soldQuantity, 0)

  return (
    <div style={{ maxWidth: '1440px', margin: '0 auto', padding: '24px 20px' }}>
      
      {/* Top Navigation / Header */}
      <header className="glass-panel" style={{ padding: '20px 24px', marginBottom: '24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <span className="pulse-dot pulse-green"></span>
            <h1 style={{ fontSize: '1.45rem', fontWeight: '700', letterSpacing: '-0.02em' }}>
              Real-Time E-Commerce Order Orchestration Platform
            </h1>
            <span style={{ fontSize: '0.75rem', background: 'rgba(99, 102, 241, 0.2)', color: '#a5b4fc', padding: '3px 8px', borderRadius: '6px', border: '1px solid rgba(99, 102, 241, 0.4)' }}>
              Live Engine v1.0
            </span>
          </div>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginTop: '4px' }}>
            Built by Team Triumph Squad | Garimalla Kiran Sai, Maram Akash, Manoj Kumar, Durga Vamsi Krishnam Raju
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <button className="btn-secondary" onClick={resetInventory} title="Reset warehouse stocks to initial 100 units">
            ↺ Reset Inventory (100 Units)
          </button>
          <button className="btn-secondary" onClick={() => setShowDlqModal(true)} style={{ position: 'relative' }}>
            Dead Letter Queue
            {dlqItems.length > 0 && (
              <span style={{ background: '#8b5cf6', color: 'white', borderRadius: '9999px', padding: '2px 7px', fontSize: '0.7rem', fontWeight: 'bold' }}>
                {dlqItems.length}
              </span>
            )}
          </button>
        </div>
      </header>

      {/* Metrics Row */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px', marginBottom: '24px' }}>
        
        <div className="glass-panel" style={{ padding: '18px 20px' }}>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: '600' }}>Total Orders</div>
          <div style={{ fontSize: '1.8rem', fontWeight: '700', marginTop: '6px' }}>{metrics.totalOrders}</div>
          <div style={{ fontSize: '0.75rem', color: '#9ca3af', marginTop: '4px' }}>Incoming burst throughput</div>
        </div>

        <div className="glass-panel" style={{ padding: '18px 20px', borderLeft: '3px solid #10b981' }}>
          <div style={{ fontSize: '0.8rem', color: '#34d399', textTransform: 'uppercase', fontWeight: '600' }}>Confirmed & Fulfilled</div>
          <div style={{ fontSize: '1.8rem', fontWeight: '700', color: '#10b981', marginTop: '6px' }}>{metrics.confirmedOrders}</div>
          <div style={{ fontSize: '0.75rem', color: '#9ca3af', marginTop: '4px' }}>Stock allocated & paid</div>
        </div>

        <div className="glass-panel" style={{ padding: '18px 20px', borderLeft: '3px solid #f43f5e' }}>
          <div style={{ fontSize: '0.8rem', color: '#fb7185', textTransform: 'uppercase', fontWeight: '600' }}>Out of Stock</div>
          <div style={{ fontSize: '1.8rem', fontWeight: '700', color: '#f43f5e', marginTop: '6px' }}>{metrics.outOfStockOrders}</div>
          <div style={{ fontSize: '0.75rem', color: '#9ca3af', marginTop: '4px' }}>Safely rejected (0 negative)</div>
        </div>

        <div className="glass-panel" style={{ padding: '18px 20px', borderLeft: '3px solid #8b5cf6' }}>
          <div style={{ fontSize: '0.8rem', color: '#c084fc', textTransform: 'uppercase', fontWeight: '600' }}>Dead Letter Queue</div>
          <div style={{ fontSize: '1.8rem', fontWeight: '700', color: '#8b5cf6', marginTop: '6px' }}>{metrics.dlqOrders}</div>
          <div style={{ fontSize: '0.75rem', color: '#9ca3af', marginTop: '4px' }}>Exceeded 3 retry attempts</div>
        </div>

        <div className="glass-panel" style={{ padding: '18px 20px', borderLeft: '3px solid #06b6d4' }}>
          <div style={{ fontSize: '0.8rem', color: '#38bdf8', textTransform: 'uppercase', fontWeight: '600' }}>In Thread Pool</div>
          <div style={{ fontSize: '1.8rem', fontWeight: '700', color: '#06b6d4', marginTop: '6px' }}>{metrics.processingOrders}</div>
          <div style={{ fontSize: '0.75rem', color: '#9ca3af', marginTop: '4px' }}>Active worker threads</div>
        </div>

      </div>

      {/* Flash Sale Simulation Cockpit */}
      <section className="glass-panel" style={{ padding: '24px', marginBottom: '24px', background: 'linear-gradient(135deg, rgba(30, 27, 75, 0.4) 0%, rgba(17, 24, 39, 0.8) 100%)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
          <div>
            <h2 style={{ fontSize: '1.15rem', fontWeight: '700', display: 'flex', alignItems: 'center', gap: '8px' }}>
              ⚡ High-Concurrency Flash Sale Simulator
            </h2>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginTop: '4px', maxWidth: '650px' }}>
              Submits hundreds of parallel requests in the exact same millisecond. Tests Thread Pool execution, Row-Level Locking (<code style={{ color: '#a5b4fc' }}>PESSIMISTIC_WRITE</code>), and verifies inventory <strong>NEVER drops below 0</strong>.
            </p>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '16px', flexWrap: 'wrap' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <label style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Burst Volume:</label>
              <select 
                value={burstCount} 
                onChange={(e) => setBurstCount(Number(e.target.value))}
                style={{ background: '#1f2937', color: 'white', border: '1px solid var(--border-color)', padding: '8px 14px', borderRadius: '8px', fontWeight: '600' }}
              >
                <option value={20}>20 Orders</option>
                <option value={50}>50 Orders</option>
                <option value={100}>100 Orders</option>
                <option value={200}>200 Orders</option>
              </select>
            </div>

            <button 
              className="btn-primary" 
              onClick={triggerBurst} 
              disabled={isBursting}
              style={{ padding: '12px 24px', fontSize: '0.95rem' }}
            >
              {isBursting ? 'Processing Concurrency...' : `⚡ Launch ${burstCount} Concurrent Orders`}
            </button>
          </div>
        </div>
      </section>

      {/* Multi-Warehouse Live Inventory Overview */}
      <section style={{ marginBottom: '24px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
          <h2 style={{ fontSize: '1.1rem', fontWeight: '700' }}>
            Multi-Warehouse Inventory Hub (SKU: PROD-PHONE)
          </h2>
          <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
            Total Available: <strong style={{ color: totalAvailable > 0 ? '#34d399' : '#f43f5e' }}>{totalAvailable}</strong> | 
            Reserved: <strong style={{ color: '#fbbf24' }}> {totalReserved}</strong> | 
            Sold: <strong style={{ color: '#818cf8' }}> {totalSold}</strong>
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '16px' }}>
          {inventory.map(item => {
            const totalCapacity = item.availableQuantity + item.reservedQuantity + item.soldQuantity
            const percentAvailable = totalCapacity > 0 ? (item.availableQuantity / totalCapacity) * 100 : 0

            return (
              <div key={item.id} className="glass-panel" style={{ padding: '18px 20px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontWeight: '700', fontSize: '1.05rem' }}>{item.warehouseName} Warehouse</span>
                  {item.availableQuantity === 0 ? (
                    <span style={{ fontSize: '0.7rem', color: '#f43f5e', background: 'rgba(244, 63, 94, 0.15)', padding: '2px 6px', borderRadius: '4px', fontWeight: '600' }}>DEPLETED</span>
                  ) : (
                    <span style={{ fontSize: '0.7rem', color: '#34d399', background: 'rgba(16, 185, 129, 0.15)', padding: '2px 6px', borderRadius: '4px', fontWeight: '600' }}>IN STOCK</span>
                  )}
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '14px', fontSize: '0.85rem' }}>
                  <span style={{ color: 'var(--text-secondary)' }}>Available Stock:</span>
                  <span style={{ fontWeight: '700', color: item.availableQuantity > 0 ? '#34d399' : '#f43f5e', fontSize: '1.1rem' }}>
                    {item.availableQuantity}
                  </span>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '6px', fontSize: '0.85rem' }}>
                  <span style={{ color: 'var(--text-secondary)' }}>Reserved / Sold:</span>
                  <span style={{ color: 'var(--text-primary)' }}>{item.reservedQuantity} res / {item.soldQuantity} sold</span>
                </div>

                {/* Progress bar */}
                <div style={{ width: '100%', height: '6px', background: '#1f2937', borderRadius: '9999px', marginTop: '12px', overflow: 'hidden' }}>
                  <div style={{ width: `${percentAvailable}%`, height: '100%', background: percentAvailable > 20 ? '#10b981' : '#f43f5e', transition: 'width 0.3s ease' }}></div>
                </div>
              </div>
            )
          })}
        </div>
      </section>

      {/* Live Orders Stream & Audit Trail */}
      <section className="glass-panel" style={{ padding: '20px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', flexWrap: 'wrap', gap: '12px' }}>
          <div>
            <h2 style={{ fontSize: '1.1rem', fontWeight: '700' }}>Live Order Stream</h2>
            <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Real-time state transitions through the worker thread pool</p>
          </div>

          <div style={{ display: 'flex', gap: '8px' }}>
            {['ALL', 'CONFIRMED', 'OUT_OF_STOCK', 'DLQ', 'PROCESSING'].map(tab => (
              <button 
                key={tab}
                onClick={() => setActiveTab(tab)}
                style={{
                  background: activeTab === tab ? '#374151' : 'transparent',
                  color: activeTab === tab ? '#ffffff' : 'var(--text-secondary)',
                  border: '1px solid var(--border-color)',
                  padding: '6px 12px',
                  borderRadius: '6px',
                  fontSize: '0.75rem',
                  cursor: 'pointer',
                  fontWeight: activeTab === tab ? '600' : '400'
                }}
              >
                {tab.replace('_', ' ')}
              </button>
            ))}
          </div>
        </div>

        {/* Orders Table */}
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--border-color)', color: 'var(--text-muted)' }}>
                <th style={{ padding: '12px 14px' }}>Order #</th>
                <th style={{ padding: '12px 14px' }}>Customer</th>
                <th style={{ padding: '12px 14px' }}>Destination</th>
                <th style={{ padding: '12px 14px' }}>Warehouse</th>
                <th style={{ padding: '12px 14px' }}>Status</th>
                <th style={{ padding: '12px 14px' }}>Retries</th>
                <th style={{ padding: '12px 14px' }}>Details / Reason</th>
                <th style={{ padding: '12px 14px' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredOrders.length === 0 ? (
                <tr>
                  <td colSpan={8} style={{ textAlign: 'center', padding: '36px', color: 'var(--text-muted)' }}>
                    No orders in this view yet. Launch the Flash Sale simulator above to fire orders!
                  </td>
                </tr>
              ) : (
                filteredOrders.map(order => (
                  <tr key={order.id} style={{ borderBottom: '1px solid rgba(255,255,255,0.04)', transition: 'background 0.15s' }}>
                    <td style={{ padding: '12px 14px', fontFamily: 'monospace', color: '#93c5fd' }}>{order.orderNumber}</td>
                    <td style={{ padding: '12px 14px' }}>{order.customerName}</td>
                    <td style={{ padding: '12px 14px' }}>{order.destinationCity}</td>
                    <td style={{ padding: '12px 14px' }}>{order.allocatedWarehouse || '—'}</td>
                    <td style={{ padding: '12px 14px' }}>{getStatusBadge(order.status)}</td>
                    <td style={{ padding: '12px 14px', textAlign: 'center' }}>
                      {order.retryCount > 0 ? (
                        <span style={{ color: '#fbbf24', fontWeight: 'bold' }}>{order.retryCount} / 3</span>
                      ) : '0'}
                    </td>
                    <td style={{ padding: '12px 14px', color: 'var(--text-secondary)', maxWidth: '280px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {order.failureReason || 'Processing normally'}
                    </td>
                    <td style={{ padding: '12px 14px' }}>
                      <button 
                        onClick={() => viewTimeline(order.id)}
                        style={{ background: 'rgba(255,255,255,0.06)', border: 'none', color: '#a5b4fc', padding: '4px 10px', borderRadius: '4px', cursor: 'pointer', fontSize: '0.75rem' }}
                      >
                        Audit Trail
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* Dead Letter Queue Modal */}
      {showDlqModal && (
        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.8)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100, padding: '20px' }}>
          <div className="glass-panel" style={{ width: '100%', maxWidth: '850px', maxHeight: '85vh', display: 'flex', flexDirection: 'column', padding: '24px', background: '#111827' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <div>
                <h3 style={{ fontSize: '1.2rem', fontWeight: '700', color: '#c084fc' }}>⚠ Dead Letter Queue (DLQ)</h3>
                <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Orders that failed all 3 retry attempts and were quarantined for inspection.</p>
              </div>
              <button onClick={() => setShowDlqModal(false)} style={{ background: 'transparent', border: 'none', color: 'white', fontSize: '1.2rem', cursor: 'pointer' }}>✕</button>
            </div>

            <div style={{ overflowY: 'auto', flex: 1 }}>
              {dlqItems.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
                  Dead Letter Queue is empty. No failed orders have exceeded retry limits.
                </div>
              ) : (
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
                  <thead>
                    <tr style={{ borderBottom: '1px solid var(--border-color)', color: 'var(--text-muted)' }}>
                      <th style={{ padding: '10px' }}>Order #</th>
                      <th style={{ padding: '10px' }}>Reason</th>
                      <th style={{ padding: '10px' }}>Retries</th>
                      <th style={{ padding: '10px' }}>Timestamp</th>
                      <th style={{ padding: '10px' }}>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dlqItems.map(item => (
                      <tr key={item.id} style={{ borderBottom: '1px solid rgba(255,255,255,0.04)' }}>
                        <td style={{ padding: '10px', fontFamily: 'monospace', color: '#93c5fd' }}>{item.orderNumber}</td>
                        <td style={{ padding: '10px', color: '#f87171' }}>{item.failureReason}</td>
                        <td style={{ padding: '10px' }}>{item.retryCount} attempts</td>
                        <td style={{ padding: '10px', color: 'var(--text-muted)', fontSize: '0.75rem' }}>{item.failedAt}</td>
                        <td style={{ padding: '10px' }}>
                          {item.status === 'REPROCESSED' ? (
                            <span style={{ color: '#34d399', fontSize: '0.75rem' }}>Re-queued</span>
                          ) : (
                            <button 
                              onClick={() => reprocessDlq(item.id)}
                              style={{ background: '#8b5cf6', border: 'none', color: 'white', padding: '4px 10px', borderRadius: '4px', cursor: 'pointer', fontSize: '0.75rem', fontWeight: '600' }}
                            >
                              Reprocess
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Order Audit Trail Modal */}
      {selectedTimeline && (
        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.8)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100, padding: '20px' }}>
          <div className="glass-panel" style={{ width: '100%', maxWidth: '650px', maxHeight: '80vh', display: 'flex', flexDirection: 'column', padding: '24px', background: '#111827' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h3 style={{ fontSize: '1.15rem', fontWeight: '700' }}>
                Order Lifecycle Timeline (ID: {selectedTimeline.orderId})
              </h3>
              <button onClick={() => setSelectedTimeline(null)} style={{ background: 'transparent', border: 'none', color: 'white', fontSize: '1.2rem', cursor: 'pointer' }}>✕</button>
            </div>

            <div style={{ overflowY: 'auto', flex: 1, paddingRight: '8px' }}>
              {selectedTimeline.logs.length === 0 ? (
                <div style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '20px' }}>No audit events logged yet.</div>
              ) : (
                <div style={{ borderLeft: '2px solid #374151', paddingLeft: '18px', marginLeft: '8px' }}>
                  {selectedTimeline.logs.map((log, idx) => (
                    <div key={idx} style={{ marginBottom: '18px', position: 'relative' }}>
                      <div style={{ position: 'absolute', left: '-24px', top: '4px', width: '10px', height: '10px', borderRadius: '50%', background: '#6366f1' }}></div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ fontWeight: '600', fontSize: '0.9rem', color: '#e5e7eb' }}>{log.stage}</span>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{log.timestamp?.split('T')[1]?.substring(0, 8)}</span>
                      </div>
                      <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginTop: '2px' }}>{log.details}</div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

    </div>
  )
}
